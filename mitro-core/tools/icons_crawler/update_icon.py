import json
import subprocess
import sys
import urllib2
from urlparse import urlparse


def write_service_json(filename, service):
    f = open(filename, 'w+')
    f.write(json.dumps(service, indent=2))
    f.close()

def get_scheme(url):
    return urlparse(url).scheme

def get_canonical_host(url):
    host = urlparse(url).netloc
    if host.startswith('www.'):
        return host[4:]
    else:
        return host

def get_default_favicon(url):
    favicon_url = '%s://%s/favicon.ico' % (get_scheme(url),
                                           get_canonical_host(url))
    try:
        result = urllib2.urlopen(favicon_url, timeout=3)
    except:
        return []
    
    if result.getcode() == 200:
        return [favicon_url]
    else:
        return []

def get_icons_and_title_for_url(url):
    host_url = '%s://%s/' % (get_scheme(url),
                             get_canonical_host(url))
    process = subprocess.Popen(['casperjs', 'get_icons.js', host_url], 
                               stdout=subprocess.PIPE)
    result = process.communicate()
    try:
        data = json.loads(result[0])
        if len(data['icons']) == 0:
            data['icons'] = get_default_favicon(url)
    except Exception:
        data = {}

    return data

def main():
    url = sys.argv[1]
    result = get_icons_and_title_for_url(url)

    output_file = 'output/' + get_canonical_host(url) + '.json'
    write_service_json(output_file, result)

if __name__ == '__main__':
    main()
