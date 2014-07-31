# Reads a list of urls and an old list of services and outputs the list
# of hostnames that are missing from the old list of services.
#
# List of urls can be generated from the mitro db using:
#
# psql -P tuples_only=on -c "select hostname from secrets" mitro
#

from collections import defaultdict
import json
import operator
import subprocess
import sys
import urllib2
from urlparse import urlparse


def read_urls_file(filename):
    f = open(filename, 'r')
    urls_list = [line.strip() for line in f]
    f.close()
    return urls_list

def read_service_list(filename):
    f = open(filename, 'r')
    service_list = json.loads(f.read())
    f.close()
    return service_list

def write_hosts_file(filename, hosts):
    f = open(filename, 'w')
    for host in hosts:
        f.write('http://' + host + '\n')
    f.close()

def get_canonical_host(url):
    host = urlparse(url).netloc
    if host.startswith('www.'):
        return host[4:]
    else:
        return host

def main():
    if len(sys.argv) != 4:
        print 'usage: old_service_list urls output_file'

    urls_list = read_urls_file(sys.argv[2]);
    service_list = read_service_list(sys.argv[1])

    service_map = defaultdict(int)

    for service in service_list:
       service_map[get_canonical_host(service['login_url'])] += 1

    hosts = []

    for url in urls_list:
        host = get_canonical_host(url)
        if host not in service_map:
            hosts.append(host)
        service_map[host] += 1

    write_hosts_file(sys.argv[3], hosts)


if __name__ == '__main__':
    main()
