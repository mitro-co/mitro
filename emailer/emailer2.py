#!/usr/bin/env python

'''Emailer2: Send users emails

Uses Amazon SES to send emails.

To create a new template:
1. Write the HTML for the template into templates/templatename_html.html
2. Convert to email using http://premailer.dialect.ca/ . Save as templates/templatename.html
    WARNING: Premailer will escape templates ({{}}) in links; manually fix these
3. Save a plain text version as templates/templatename.txt
4. Add type string to email_queue.py as _TYPE_(NAME), add to _VALID_TYPES
5. Add a new else if to _loop_once
6. Write the function to send the email (copy the existing examples?)
'''


import datetime
import json
import logging

import sqlalchemy
import sqlalchemy.exc
import sqlalchemy.ext.declarative
import sqlalchemy.orm
from sqlalchemy.pool import StaticPool
from sqlalchemy.pool import NullPool

import tornado.options

from auth import email_queue
from auth import models2
from auth import statsd

Session = sqlalchemy.orm.sessionmaker()

_once = False


def connect_to_database(url):
    global _once
    assert not _once
    _once = True

    engine = sqlalchemy.create_engine(url, poolclass=sqlalchemy.pool.NullPool, echo=False)
    Session.configure(bind=engine)


def main():
    logging.root.setLevel(logging.INFO)
    connect_to_database('postgres:///mitro')
    extra_args = tornado.options.parse_command_line()

    # Verify that mandrill is configured
    if tornado.options.options.enable_email:
        assert len(tornado.options.options.mandrill_api_key) > 0

    # Datadog listens for statsd requests on port 8125
    statsd_client = statsd.StatsdClient('localhost', 8125, 'emailer')
    backoff_sleeper = email_queue.BackoffSleeper()

    email_queue_type = models2.EmailQueue

    while True:
        try:
            email_queue.poll_forever(Session, email_queue_type, statsd_client)
        except sqlalchemy.exc.OperationalError, e:
            # In case Postgres is not running, retry the connection a few times before dying.
            # This is long enough to get upstart to keep restarting the emailer
            if not backoff_sleeper.shouldRetryAfterSleep():
                logging.error('Failing after %d retries', backoff_sleeper.max_retries())
                raise

            logging.error('SQLAlchemy exception; retrying after timeout')


if __name__ == '__main__':
    main()
