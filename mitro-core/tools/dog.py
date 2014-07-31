#!/usr/bin/python

from collections import defaultdict
import argparse
import json
import re
import struct
import sys
import time
import os


POINTER_FILE_SUFFIX = '.pointers'


parser = argparse.ArgumentParser(description='search logs for stuff. Usage: ./dog.py --fields=identity,endpoint /tmp/logfile')
parser.add_argument('files', nargs='?', help="log files")
parser.add_argument('--fields', help="fields to recursively show, comma-separated")
parser.add_argument('--dir', help="dir to look for files if not specfied", default='/mnt/rpclogs')
parser.add_argument('--matches', help="fields to recursively match, comma-separated", default='')
parser.add_argument('--showencrypted', help="show encrypted things", type=bool, default=False)
parser.add_argument('--tail', help="keep waiting for new data", action='store_true', default=False)
parser.add_argument('--gobackpct', type=float, help="pct of file to go back (between 0 and 1, double)", default=None)

SIZE_MAP = {4 : 'i',
            8 : 'q'}
def read_be(fd, size):
  buf =  fd.read(size)
  assert size in SIZE_MAP.keys(), 'invalid size'
  if len(buf) == 0: return None
  return struct.unpack('>' + SIZE_MAP[size], buf)[0]

def write_be(fd, size, value):
  assert size in SIZE_MAP.keys(), 'invalid size'
  fd.write(struct.pack('>' + SIZE_MAP[size], value))


def get_or_create_pointers(reader):
  # try to find pointer file.
  pointer_file_path = reader._filename + '.pointers'

  if not os.path.isfile(pointer_file_path):
    temp_pointer_path = reader._filename + '.tmp_pointer_file'
    print >> sys.stderr, 'warning: writing missing pointers file'

    ptr_fd = open(temp_pointer_path, 'wb')
    last_write = 0
    recno = 0
    WRITE_EVERY = 1 << 16
    while reader.read():
      recno += 1
      offset = reader.get_offset()
      if offset - last_write >= WRITE_EVERY:
        sys.stderr.write('.')
        write_be(ptr_fd, 8, recno)
        write_be(ptr_fd, 8, offset)
        last_write = offset
    ptr_fd.close()
    reader.seek_to_start()
    sys.stderr.write('DONE\n')

    os.rename(temp_pointer_path, pointer_file_path)

  return PointerRecord(pointer_file_path)


class PointerRecord(object):
  def __init__(self, pointer_file_path):
    self._pointers = []

    pointer_fd = open(pointer_file_path, 'rb')
    while True:
      key = read_be(pointer_fd, 8)
      if key is None:
        break
      value = read_be(pointer_fd, 8)
      self._pointers.append((key, value))
    pointer_fd.close()

  def get_back_offset_percent(self, go_back_percent):
    go_back = len(self._pointers) * go_back_percent
    key, offset = self._pointers[-int(go_back) - 1]  # negative indexing requires subtracting 1
    return offset


class RecordReader(object):
  def __init__(self, filename, go_back_percent=None):
    self._filename = filename
    self._fd = open(filename, 'rb')
    self._pointer_record = None

    # read the header
    header_version = read_be(self._fd, 4)
    assert header_version == 0

    if go_back_percent is not None:
      self._pointer_record = get_or_create_pointers(self)

      # calculate offset we want to go to
      offset = self._pointer_record.get_back_offset_percent(go_back_percent)
      print >> sys.stderr, 'seeking to %ld'  % offset
      self._fd.seek(offset, 0)

  def read(self):
    FILE_FOOTER = 0
    REVERSE_POINTER = 1
    DATA_UNCOMPRESSED = 2
    DATA_COMPRESSED = 3
    record_type = read_be(self._fd, 4)
    if record_type != DATA_UNCOMPRESSED:
      assert record_type == FILE_FOOTER or record_type is None
      return None
    else:
      length = read_be(self._fd, 4)
      buf = u''
      if length:
        buf = self._fd.read(length)
        buf = buf.decode('utf-8')
      return buf

  def seek_to_start(self):
    self._fd.seek(4, 0)

  def get_offset(self):
    return self._fd.tell()


def iso_time_string(epoch_ms):
  '''Returns epoch_ms in log4j/Java's ISO 8601 format.'''

  # ISO 8601 output
  ms = epoch_ms % 1000
  seconds = epoch_ms / 1000
  tuple_time = time.gmtime(seconds)
  timezone_suffix = 'Z'

  time_string = time.strftime('%Y-%m-%d %H:%M:%S', tuple_time) + ',%03d' % (ms) + timezone_suffix
  return time_string


def printj(obj):
  # reformat timestamps to match log4j (ISO 8061)
  if 'metadata' in obj and 'timestamp' in obj['metadata']:
    obj['metadata']['timestampString'] = iso_time_string(obj['metadata']['timestamp'])
  print json.dumps(obj, sort_keys=True,
                   indent=4, separators=(',', ': '))


def findall(obj, field_values, matching_fields=None):
  if matching_fields == None:
    matching_fields = defaultdict(list)
  for k, v in obj.iteritems():
    for fieldname, fieldvalue in field_values:
      if re.match(fieldname, k):
        if v in matching_fields[k]: 
          continue
        if type(v) == unicode or type(v) == str:
          if fieldvalue:
            if re.match(fieldvalue, v):
              matching_fields[k].append(v)
            else:
              pass
          else:
            matching_fields[k].append(v)
        else:
          matching_fields[k].append(v)
      elif type(v) == dict:
        findall(v, field_values, matching_fields)
  return matching_fields

def removecrypt(obj):
  if not type(obj) == dict:
    return obj
  for k, v in obj.iteritems():
    if (('encrypted') in k.lower()) or 'publickey' in k.lower() or k == 'signature':
      obj[k] = 'use --showencrypted to display'
    elif type(v) == dict:
      obj[k] = removecrypt(v)
    elif type(v) == list:
      obj[k] = map(removecrypt, v)
  return obj


def make(fields):
  field_values = None
  if fields:
    field_values = []
    for f in fields:
      parts = f.split('=')
      if len(parts) == 1:
        field_values.append((parts[0], None))
      else:
        field_values.append(parts)
  return field_values


def execute_matches(record, match_values):
  assert len(match_values) > 0
  match_count = 0
  for key, value in record.iteritems():
    if type(value) == dict:
      if execute_matches(value, match_values):
        return True
    else:
      for field_re, value_re in match_values:
        if re.match(field_re, key):
          if not re.match(value_re, value):
            # this doesn't match but there might be a matching value somewhere else in the structure
            break
          match_count += 1
          if match_count == len(match_values):
            return True

  return False



def main(files, fields, matches, showEncrypted):
  field_values = make(fields)

  # Convert matches string argument to list of tuples
  match_values = []
  if args.matches:
    for match in args.matches.split(','):
      field_re, value_re = match.split('=')
      match_values.append((field_re, value_re))
  if args.tail and not args.gobackpct: args.gobackpct = 0.0
  for fn in files:
    rr = RecordReader(fn, args.gobackpct)
    buf = 1
    while True:
      buf = rr.read()
      if not buf:
        if args.tail: 
          time.sleep(0.25)
          continue
        else:
          break

      log = json.loads(buf)
      data = log['payload']
      data['request'] = json.loads(data['request'])
      if not data['request']:
        data['request'] = {}
      if not showEncrypted:
        removecrypt(log)

      # Check if the match values match this record
      if len(match_values) > 0 and not execute_matches(log, match_values):
        continue

      if not fields:
        print
        print '*' * 80
        print
        printj(log)
      else:
        dat = findall(log, field_values)
        if dat:
          print
          print '*' * 80
          print
          printj(dat)


if __name__ == '__main__':
  args = parser.parse_args()

  if not args.files:
    possible_files = []
    for f in os.listdir(args.dir):
      if (f.startswith('rpclog.') or f.startswith('mitro_default_rpc_log.')) and not f.endswith(POINTER_FILE_SUFFIX):
        possible_files.append(os.path.join(args.dir, f))

    assert possible_files, "no files specified and could not find file in %s" % args.dir
    f = max(possible_files, key=os.path.getmtime)
    print >> sys.stderr, "no files specified, using %s" % f
    args.files = f

  exit_code = main(
    args.files.split(' '),
    None if not args.fields else set(args.fields.split(',')),
    args.matches,
    args.showencrypted,
  )
  sys.exit(exit_code)
