#!/usr/bin/env python

import csv
import sys
from collections import defaultdict
from urlparse import urlparse
from generate_usage_graphs import runquery
from collections import defaultdict
import time

raw = runquery('''select count(1), hostname from secrets join group_secret on ("serverVisibleSecret_id" = secrets.id) join acl on (group_secret.group_id = acl.group_id) group by secrets.id, hostname order by count(1) desc''')
#reader = csv.reader(open(sys.argv[1], 'rU'))
#raw = [r for r in reader]

processed = []
hosts = defaultdict(int)
shared = defaultdict(int)
for (count, hostname) in raw:
    nl = urlparse(hostname).netloc
    if not nl: nl = hostname
    processed.append((count,nl))
    hosts[nl] += int(count)
    if int(count) > 1:
        shared[nl] += int(count)


coalesced = hosts.items()
coalesced.sort(key=lambda x:x[1], reverse=True)
print 'most saved sites'
for i in range(100):
    print '\t'.join(map(str,coalesced[i]))
print 
print 
coalesced = shared.items()
coalesced.sort(key=lambda x:x[1], reverse=True)
print 'most shared sites'
for i in range(100):
    print '\t'.join(map(str,coalesced[i]))

MS_PER_DAY = 86400000
class UserInfo:
    def __init__(self, name):
        self._name = name
        self._first_action = 1e63
        self._last_action = 0
        self._num_shared = 0
        self._num_used = 0
        self._num_added = 0

    def set(self, action, count, mint, maxt):
        if action == 'GET_PRIVATE_KEY':
            self._first_action = min(self._first_action, mint)
        if action == 'GET_SECRET_WITH_CRITICAL':
            self._last_action = max(self._last_action, maxt)
            self._num_used += count
        if action == 'MODIFY_GROUP':
            self._last_action = max(self._last_action, maxt)
            self._num_shared += count
        if action == 'ADD_SECRET':
            self._last_action = max(self._last_action, maxt)
            self._num_added += count

    def duration_days(self):
        return self.get_last() - self.get_first()

    def get_last(self):
        return self._last_action / MS_PER_DAY

    def get_first(self):
        return 0 if not self.has_logged_in() else self._first_action / MS_PER_DAY

    def _today(self):
        return (time.time()*1000)/MS_PER_DAY


    def has_logged_in(self):
        return self._first_action < 1e62
    def is_good_nonsharing(self):
        '''
        non-sharing good users:
        users who've used the site for at least 7 days (max - min)
        users who have saved at least 3 passwords and logged in to at least 3 sites
        and not shared any secrets.
        And have used the site in the last 7 days
        '''

        return (self.duration_days() >= 7 and self._num_added > 3 and self._num_used > 3 and self._num_shared == 0
                and (self._today() - self.get_last() < 7))


    def is_good_sharing(self):
        return (self.duration_days() >= 7 and self._num_added > 3 and self._num_used > 3 and self._num_shared > 3
                and (self._today() - self.get_last() < 7))

    def is_not_using(self):
        return (self.has_logged_in() and self._num_added == 0 and self._num_used == 0)

    def is_abandoned(self):
        return (self.has_logged_in() and self._num_added >= 2 and self._num_used >= 2
                and (self._today() - self.get_last() >= 7))

    def name(self):
        return self._name

    def __str__(self):

        return '%s: duration = %s; today=%s first=%s; last=%s; shares=%d; saves=%d; logins=%d good_nonshare=%s good_sharing=%s not_using=%s is_abandoned=%s' % (
            self._name, self.duration_days(), self._today(), self.get_first(), self.get_last(),  
            self._num_shared, self._num_added, self._num_used,
            self.is_good_nonsharing(), self.is_good_sharing(), self.is_not_using(), self.is_abandoned())

q = '''SELECT  name, action, count(1), min("timestampMs"), max("timestampMs") from audit join identity on (identity.id = uid) group by name, action order by name;'''
raw = runquery(q)
users = {}
names = set()
for (name, action, count, mint, maxt) in raw:
    if name not in users:
        users[name] = UserInfo(name)

    users[name].set(action, count, mint, maxt)
    names.add(name)

nonshare = [u for u in users.values() if u.is_good_nonsharing()]
good = [u for u in users.values() if u.is_good_sharing()]
notusing = [u for u in users.values() if u.is_not_using()]
abandoned = [u for u in users.values() if u.is_abandoned()]
print

def make_tuple(l):
    return (len(l), 100.0 * len(l) / len(names))

print 
print
print 'Number of good        users: %d (%d%%)'% make_tuple(good)
print 'Number of non-sharing users: %d (%d%%)'% make_tuple(nonshare)
print 'Number of non-using   users: %d (%d%%)'% make_tuple(notusing)
print 'Number of abandoned   users: %d (%d%%)'% make_tuple(abandoned)
print 'TOTAL USERS                : %d (%d%%)'% make_tuple(names)
print
print
print 'details'

print 'not sharing'
print '\n'.join(map(str, nonshare))
print
print
print 'good'
print '\n'.join(map(str, good))
print
print
print 'not using'
print '\n'.join(map(str, notusing))
print
print
print 'abandoned'
print '\n'.join(map(str, abandoned))


print 'not sharing'
print ','.join(map(UserInfo.name, nonshare))
print
print
print 'good'
print ','.join(map(UserInfo.name, good))
print
print
print 'not using'
print ','.join(map(UserInfo.name, notusing))
print
print
print 'abandoned'
print ','.join(map(UserInfo.name, abandoned))


# non-sharing "bad" users
# users who've used the site for at least 1 day
# users who've 

# abandoned users
# users who've added secrets and either deleted all of them or no longer use the site.







