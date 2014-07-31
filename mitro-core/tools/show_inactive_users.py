#!/usr/bin/python
import popen2
import csv
import time
import psycopg2
import pprint
import sys
conn_string = "host='localhost' dbname='mitro'"
conn = psycopg2.connect(conn_string)
cursor = conn.cursor()
cursor.execute('''select *, ts_max-ts_min as duration from (select max("timestampMs") as ts_max, min("timestampMs") as ts_min, count(1) as count, name from audit, identity where uid = identity.id and name not like '%@lectorius.com' and name not like '%mitro.co' and action in ('GET_SECRET_WITH_CRITICAL', 'ADD_SECRET', 'ADD_GROUP') group by name order by ts_max desc) as foo''')
records = cursor.fetchall()

for row in records:
    print ('last use:', time.ctime(int(row[0])/1000), 
        #'first use:', time.ctime(int(row[1])/1000), 
        'days:', int(row[4])/1000/3600/24,
        'count:',row[2], 
        row[3])
    
