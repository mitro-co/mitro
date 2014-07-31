#!/usr/bin/python
import popen2
import csv
import time
import psycopg2
import pprint
import sys
import os
import datetime
import csv
import matplotlib.pyplot as plt
from collections import defaultdict
def runquery(q):
    conn_string = "host='localhost' dbname='mitro'"
    conn = psycopg2.connect(conn_string)
    cursor = conn.cursor()
    cursor.execute(q)
    records = cursor.fetchall()
    return records



    return raw

def main(directory, csv_file=None):
    MAX_TICKS = 20
    def make_line(raw, title, xlabel, ylabel, filename):
        # raw[0] is titles

        plt.clf()
        plt.cla()
        plt.figure(figsize=(8.0, 5.0))
        num_values = len(raw[1]) - 1
        for index in range(num_values):
            y = [r[index + 1] for r in raw[1:]]
            plt.plot(y, label=raw[0][index + 1])
        plt.title(title)
        plt.xlabel(xlabel)
        plt.ylabel(ylabel)

        xticks = [r[0] for r in raw[1:]]
        plt.gca().set_xticks(range(len(xticks)))
        show_every = len(xticks) / MAX_TICKS
        if show_every:
            xticks = [x if not i % show_every else '' for (i,x) in enumerate(xticks)]
        plt.gca().set_xticklabels(xticks, rotation=90, size='xx-small')

        plt.legend(loc=2)
        plt.savefig(os.path.join(directory, filename), dpi=600)



    def day_actives():
        def calculate_n_day(dct, days, n=7, create_map_for_exclude = None):

            rval = defaultdict(lambda: set())
            for day in sorted(days):
                day = int(day)
                for offset in range(n):
                    people_to_add = dct[day - offset]
                    rval[day] = dct[day].union(people_to_add)
                if create_map_for_exclude:
                    rval[day] = rval[day].difference(create_map_for_exclude[day])
            return rval

        q = '''SELECT  name, count(1), action, "timestampMs"/86400000 as day from audit join identity on (identity.id = uid) group by name, action, "timestampMs"/86400000 order by day;'''
        raw = runquery(q)
        total_users = defaultdict(lambda:set())
        share_secret = defaultdict(lambda:set())
        add_secret = defaultdict(lambda:set())
        use_secret = defaultdict(lambda:set())
        login = defaultdict(lambda:set())
        create = defaultdict(lambda:set())
        days = set()
        for email, count, action, day in raw:
            days.add(day)
            if action == 'GET_SECRET_WITH_CRITICAL':
                use_secret[day].add(email)
            elif action == 'ADD_SECRET':
                add_secret[day].add(email)
            elif action == 'GET_PRIVATE_KEY':
                login[day].add(email)
            elif action == 'CREATE_IDENTITY':
                create[day].add(email)
            elif action == 'MODIFY_GROUP':
                share_secret[day].add(email)

        table = [('day', 'used a secret', 'added a secret', 'logged in', 'shared a secret', 'create identity', 'total users')]
        for day in sorted(days):
            total_users[day] = total_users[day-1].union(create[day]).union(login[day]).union(add_secret[day]).union(use_secret[day]).union(share_secret[day])
            table.append((day, len(use_secret[day]),
                len(add_secret[day]),len(login[day]),len(share_secret[day]),
                len(create[day]), len(total_users[day])
                ))
        make_line(table, 'one-day-active users','day','number of users', 'one-day-active-actions.png')

        table = [('day', 'used a secret', 'added a secret', 'logged in', 'shared a secret', 'create identity', 'total users')]
        
        create = calculate_n_day(create,days)
        share_secret = calculate_n_day(share_secret, days, create_map_for_exclude=create)
        add_secret = calculate_n_day(add_secret,days, create_map_for_exclude=create)
        use_secret = calculate_n_day(use_secret,days, create_map_for_exclude=create)
        login = calculate_n_day(login,days, create_map_for_exclude=create)
        for day in sorted(days):
            table.append((day, len(use_secret[day]),
                len(add_secret[day]),len(login[day]),len(share_secret[day]),
                len(create[day]), len(total_users[day])
                ))
        make_line(table, 'seven-day-active users','day','number of users', 'seven-day-active-actions.png')


    day_actives()


    def make_hist(q, title, xlabel, ylabel, filename, process=lambda data: [x[0] for x in data], bins=10, logy=False):
        raw = runquery(q)
        hist_data = process(raw)
        plt.clf()
        plt.cla()
        plt.figure(figsize=(8.0, 5.0))
        plt.hist(hist_data, bins=bins, log=logy)
        plt.title(title)
        plt.ylabel(ylabel)
        plt.xlabel(xlabel)
        plt.savefig(os.path.join(directory, filename), dpi=600)


    if csv_file:
        reader = csv.reader(open(csv_file, 'rU'))
        raw = [r for r in reader]

        make_line(raw, 'Usage statistics', 'date', 'users', 'usage_stats.png')


    make_hist('''
SELECT count(secret), name FROM
    (SELECT distinct name, "serverVisibleSecret_id" AS secret
        FROM group_secret, identity, acl
        WHERE identity.id = acl.member_identity AND group_secret.group_id = acl.group_id
        ORDER BY name,secret) AS foo 
    GROUP BY foo.name;
''',

    'Histogram: secrets per user',
    'total secrets',
    'number of users',
    'secret_per_user_hist.png', bins=100)




    make_hist('''
select count(1), hostname from secrets join group_secret on ("serverVisibleSecret_id" = secrets.id) join acl on (group_secret.group_id = acl.group_id) group by secrets.id, hostname order by count(1) desc''', 
    'Number of users with access to secrets',
    'total users with access to secret',
    'number of secrets',
    'secret_share_hist.png', bins=50, logy=True)


    make_hist('''select *, ts_max-ts_min as duration from (select max("timestampMs") as ts_max, 
        min("timestampMs") as ts_min, count(1) as count, name from audit, identity 
        WHERE uid = identity.id and name not like '%@lectorius.com' and name NOT LIKE '%mitro.co' 
        GROUP BY name order by ts_max desc) as foo''',
        'Most recent use of site',
        'days ago',
        'number of users',
        'recently_used.png',
        lambda data: [datetime.timedelta(0, time.time() - (x[0] / 1000)).days for x in data],
        bins=200
        )

    make_hist('''select *, ts_max-ts_min as duration from (select max("timestampMs") as ts_max, 
        min("timestampMs") as ts_min, count(1) as count, name from audit, identity 
        WHERE uid = identity.id and name not like '%@lectorius.com' and name NOT LIKE '%mitro.co' 
        GROUP BY name order by ts_max desc) as foo''',
        'First use of site',
        'days ago',
        'number of users',
        'first_used.png',
        lambda data: [datetime.timedelta(0, time.time() - (x[1] / 1000)).days for x in data],
        bins=200
        )


    make_hist('''select ts_max-ts_min as duration from (select max("timestampMs") as ts_max, 
        min("timestampMs") as ts_min, count(1) as count, name from audit, identity 
        WHERE uid = identity.id and name not like '%@lectorius.com' and name NOT LIKE '%mitro.co' 
        GROUP BY name order by ts_max desc) as foo''',
        'Duration of site use',
        'number of days',
        'number of users',
        'site_duration.png',
        lambda data: [datetime.timedelta(0, (x[0] / 1000)).days for x in data], bins=200
        )


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print >>sys.stderr, 'usage: %s output_directory [input_csv_file]' % sys.argv[0]
    else:
        sys.exit(main(sys.argv[1], sys.argv[2] if len(sys.argv) >= 3 else None))


# #'''
# select *, ts_max-ts_min as duration from (select max("timestampMs") as ts_max, min("timestampMs") as ts_min, count(1) as count, name from audit, identity where uid = identity.id and name not like '%@lectorius.com' and name not like '%mitro.co' group by name order by ts_max desc) as foo
#''')
# for row in records:
#     print ('last use:', time.ctime(int(row[0])/1000), 
#         #'first use:', time.ctime(int(row[1])/1000), 
#         'days:', int(row[4])/1000/3600/24,
#         'count:',row[2], 
#         row[3])


# # number of groups per person
# #select name, count(group_id) from acl, identity where acl.member_identity = identity.id group by identity.name order by count desc;


# # number of groups each secret is shared with
# #select secrets.id, substr(hostname,0,30), count(secrets.id) from secrets, group_secret where secrets.id = "serverVisibleSecret_id" group by secrets.id order by count desc


# # number of people each secret is shared with

# SELECT id, substr(foo.hostname,0,30), count(id)
# from ( select distinct secrets.id, hostname, member_identity
#      from secrets, group_secret, acl where secrets.id = "serverVisibleSecret_id" and acl.group_id = group_secret.group_id
#      and acl.member_identity is not NULL)
# as FOO
# group by foo.id,foo.hostname order by count desc;
