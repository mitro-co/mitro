#!/usr/bin/python

'''One-time hack to fix the unencoded device ids in the database.'''

import base64
import binascii
import psycopg2


conn_string = "host='localhost' dbname='mitro'"
conn = psycopg2.connect(conn_string)
cursor = conn.cursor()
cursor.execute('''select id, device, "user" from device_specific''')
records = cursor.fetchall()

for row in records:
    row_id, device_id, identity_id = row

    # If this is base64 data, no migration needed
    try:
        byte_data = base64.decodestring(device_id).strip()
        continue
    except binascii.Error, e:
        pass

    unicode_value = device_id.decode('utf8')
    # strings were 20 chars, but null bytes got dropped
    assert len(unicode_value) <= 20

    byte_string = ''
    for codepoint in unicode_value:
        b = ord(codepoint)
        assert 0 <= b <= 255
        byte_string += chr(b)
    b64_data = base64.encodestring(byte_string).strip()

    # See if this has already been migrated. device_ids could be shared between users
    cursor.execute('''select count(*) from device_specific where device=%s and "user"=%s''',
            (b64_data, identity_id))
    count = cursor.fetchone()[0]

    if count == 0:
        print ('''insert into device_specific (SELECT ''' +
            '''nextval('device_specific_id_seq'), '%s', client_local_storage_key, ''' +
            '''last_use_sec, "user" from device_specific where id=%d);''') % (b64_data, row_id)
    else:
        assert count == 1
        print '# skipping id %d already migrated' % row_id

conn.close()
