#!/usr/bin/env python

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


import errno
import logging
import logging.handlers
import sys
import time
import os

def init_logging(level=None, filedir=None, argv=sys.argv, screen_level=None):
    if level is None:
        level = logging.INFO
    if screen_level is None:
        screen_level = logging.INFO

    ch = logging.StreamHandler()
    ch.setLevel(screen_level)
    pid = os.getpid()
    formatter = logging.Formatter(
        "%(levelname).1s%(asctime)s %(pathname)s:p" + str(pid) + "l%(lineno)d] %(message)s",
        datefmt='%Y%m%d:%H%M%S')
    ch.setFormatter(formatter)

    for LOG in [logging.getLogger(''), logging.getLogger()]:
        LOG.setLevel(0)
        LOG.addHandler(ch)
  
    if filedir:
        datestr = time.strftime('%Y%m%d-%H%M%S')
        basefilename = os.path.split(sys.argv[0])[-1]
        loggers = {'INFO': (logging.INFO, None),
                   'WARNING': (logging.WARNING, None),
                   'ERROR' : (logging.ERROR, None)}

        for (k, (level, filter)) in loggers.items():
            fn = os.path.join(filedir, '%s.%s.%s.%s' % (basefilename, datestr, pid, k))
            channel = logging.handlers.RotatingFileHandler(fn, maxBytes=100*1024*1024, backupCount=2)
            channel.setLevel(level)
            channel.setFormatter(formatter)
            if filter:
                channel.addFilter(filter())
            LOG.addHandler(channel)
            symname = os.path.join(filedir, '%s.%s' % (basefilename, k))
            try:
                # Try to symlink; Ignore permission error (eg. running as another user)
                if os.path.lexists(symname):
                    os.unlink(symname)
                os.symlink(fn, symname)
            except OSError, e:
                # EACCES is permission denied
                # EPERM on Linux for directories with sticky bit and wrong user
                # ENOENT for the first time running a process
                if e.errno not in (errno.EACCES, errno.EPERM):
                    raise
        
            logging.info('starting up. Logs are written to directory: %s',
                         filedir)