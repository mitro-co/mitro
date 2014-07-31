#!/usr/bin/python

# *****************************************************************************
# Copyright (c) 2012, 2013, 2014 Lectorius, Inc.
# Authors:
# Vijay Pandurangan (vijayp@mitro.co)
# Evan Jones (ej@mitro.co)
# Adam Hilss (ahilss@mitro.co)
#
#
#     This program is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This program is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
#     You can contact the authors at inbound@mitro.co.
# *****************************************************************************


'''Outputs a LastPass CSV file with randomly generated data.'''


import os
import base64


def random_string(chars):
  b = os.urandom(chars)
  out = base64.encodestring(b)
  return out[:chars]


def main():
  print 'url,username,password,extra,name,grouping,fav'

  for i in xrange(500):
    url = 'http://example.com/' + str(i)
    username = random_string(8)
    password = random_string(9)
    note = 'note ' + random_string(10)
    title = 'title ' + str(i) + ' ' + random_string(11)
    print '%s,%s,%s,%s,%s,,' % (url, username, password, note, title)

if __name__ == '__main__':
  main()