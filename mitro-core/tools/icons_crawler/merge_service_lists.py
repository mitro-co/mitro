import json
from os import listdir
from os.path import isfile, join
import sys

def read_service_json(filename):
    f = open(filename, 'r')
    service_list = json.loads(f.read())
    f.close()
    return service_list

def write_service_json(filename, service_list):
    f = open(filename, 'w+')
    f.write(json.dumps(service_list, indent=2))
    f.close()

def main():
    if len(sys.argv) != 4:
        print 'usage: %s old_service_list input_dir output_file' % sys.argv[0]
        exit(1)

    old_service_list = sys.argv[1]
    input_dir = sys.argv[2]
    output_file = sys.argv[3]

    service_list = read_service_json(old_service_list)

    for filename in listdir(input_dir):
        print filename
        path = join(input_dir, filename)
        if isfile(path) and path.endswith('.json'):
            service = read_service_json(path)
            service['login_url'] = 'http://' + filename[:-5]
            title = service['title'] if 'title' in service else ''
            if not (title.startswith('400') or title.startswith('403') or title.startswith('500') or title.startswith('502')):
                service_list.append(service)

    write_service_json(output_file, service_list)
                

if __name__ == '__main__':
    main()
