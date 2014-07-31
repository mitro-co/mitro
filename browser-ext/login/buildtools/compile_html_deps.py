#!/usr/bin/python

from collections import defaultdict
import argparse
import json
import re
import struct
import sys
import time
import re
import subprocess
import tempfile
import os

parser = argparse.ArgumentParser(description='compiles js which is dependant on an html file')
parser.add_argument('files', nargs='+', help="html files")
parser.add_argument('--outdir', help="output directory", default='./genfiles')
parser.add_argument('--compiler', help="compiler location", default='../third_party/closure-compiler/compiler.jar')
parser.add_argument('--compile-min-sources', dest='compile_min', help="include min.js files", action='store_true')
parser.add_argument('--no-compile-min-sources', dest='compile_min', help="include min.js files", action='store_false')
parser.add_argument('--advanced-optimizations', dest='advanced_opt', action='store_true')
parser.add_argument('--no-advanced-optimizations', dest='advanced_opt', action='store_false')
parser.add_argument('--simple-optimizations', dest='simple_opt', action='store_true')
parser.add_argument('--no-simple-optimizations', dest='simple_opt', action='store_false')
parser.add_argument('--ignore-errors', dest='ignore_errors', action='store_true')

parser.set_defaults(compile_min=False)
parser.set_defaults(simple_opt=True)
parser.set_defaults(advanced_opt=False)



#TODO: this is pretty crappy but it should work for our purposes.
MATCHER = re.compile('''< *script +[^>]*src *= *["']([^"']+)['"][^>]*> *</script>''')

HEAD_FINDER = re.compile('''</head>''')

def make_temp_filename(dir=None):
  (fd, filename) = tempfile.mkstemp(dir=dir)
  os.close(fd)
  os.unlink(filename)
  return filename

# returns an ordered list of javascript files on which this html file depends
def parse_html(html_string, ignore_minimized_sources=False):
    matches = MATCHER.findall(html_string)
    return [m for m in matches if not m.endswith('min.js')]

def remove_scripts(html_string, ignore_minimized_sources=False):
  return MATCHER.sub(lambda m:m.group(0) if m.group(1).endswith('min.js') and ignore_minimized_sources else '', html_string)

def replace_scripts(html_string, new_script_name, ignore_minimized_sources=False):
    html_string = remove_scripts(html_string, ignore_minimized_sources=ignore_minimized_sources)
    match = HEAD_FINDER.search(html_string)
    if not match:
        return None
    rval = html_string[:match.start()]
    rval += '\n<script type="text/javascript" src="%s"></script>\n' % new_script_name
    rval += html_string[match.start():]
    return rval

class Compiler(object):
  def __init__(self, compiler, advanced, outdir, externs_location='../third_party/closure-compiler/externs'):
    self._compiler = compiler
    self._outdir = outdir
    self._advanced = advanced
    self._externs_location = externs_location

  def compile(self, scripts, ignore_errors):
    args = ['java', '-jar', self._compiler, '--language_in', 'ECMASCRIPT5']
    if self._advanced:
      args += ['--compilation_level', 'ADVANCED_OPTIMIZATIONS',
      '--jscomp_off', 'globalThis',
      '--warning_level', 'VERBOSE']

    args += ['--js'] + scripts

    # include externs
    for f in os.listdir(self._externs_location):
      if f.endswith(".js"):
        args.append(os.path.join(self._externs_location,f))

    args += ['--js_output_file']
    outfile = make_temp_filename(self._outdir)
    args.append(outfile)
    print args
    if not ignore_errors:
      subprocess.check_call(args)
    else:
      subprocess.call(args)

    return outfile


def main(files, advanced_opt, compile_min, compiler, outdir, ignore_errors):
  comp = Compiler(compiler, advanced_opt, outdir)
  for fn in files:
    html_string = open(fn, 'rb').read()
    print >> sys.stderr, 'Parsing file %s' % fn
    sources = [os.path.join(os.path.dirname(fn), s) for s in parse_html(html_string, not compile_min)]
    temp_compiled_js_fn = comp.compile(sources, ignore_errors)
    try:
      new_html_filename = os.path.basename(fn).split('.')[0] + '_compiled.html'
      compiled_js_filename =  new_html_filename.split('.')[0] + '.js'
      new_html_string = replace_scripts(html_string, compiled_js_filename, not compile_min)
      assert new_html_filename != fn
      if os.path.exists(new_html_filename): 
        os.unlink(new_html_filename)
      open(os.path.join(outdir, new_html_filename), 'wb').write(new_html_string)
      os.rename(temp_compiled_js_fn, os.path.join(outdir, compiled_js_filename))
    finally:
      if os.path.exists(temp_compiled_js_fn):
        os.unlink(temp_compiled_js_fn)

    

if __name__ == '__main__':
  args = parser.parse_args()
  sys.exit(
  main(
    args.files,
    args.advanced_opt,
    args.compile_min,
    args.compiler,
    args.outdir,
    args.ignore_errors
  ))
