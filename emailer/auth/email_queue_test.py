#!/usr/bin/env python

import re
import unittest
import urllib

import mandrill
import tornado.testing

from auth import email_queue
from auth import models2
import server.db


def queue_test_invite(db, sender_name, sender_email, recipient, service_name):
    '''Queues an invitation. Note: Does not commit the transaction.'''

    email_queue._queue(db, models2.EmailQueue, email_queue._TYPE_INVITATION,
        [sender_name, sender_email, recipient, service_name, 'token_string'],
        None, None)


def queue_test_mandrill_message(db, email_type, template_name, template_params,
    subject, sender_name, sender_email, recipient_name, recipient_email):
    assert template_name
    assert isinstance(template_params, dict)
    assert '@' in recipient_email
    if template_name == 'share-to-recipient-web':
       print 'igoring message of type share-to-recipient-web'
       return
    email_queue._queue(db, models2.EmailQueue, email_type,
        [subject, sender_name, sender_email, recipient_name, recipient_email],
        template_name, template_params)


class InviteTest(unittest.TestCase):
    def test_simple(self):
        message = email_queue.generate_invite(
            u'h\u00e9llo Name', 'from@example.com', 'to@example.com', u'H\u00e9llo service', None)

        self.assertTrue(u'H\u00e9' in message['Subject'])
        self.assertEquals('from@example.com', message['Reply-To'])
        self.assertEquals('to@example.com', message['To'])
        self.assertTrue('charset="utf-8"' in message.get_payload()[0]['Content-Type'].lower())
        self.assertTrue('charset="utf-8"' in message.get_payload()[1]['Content-Type'].lower())
        self.assertTrue(u'H\u00e9' in message.get_payload()[0].get_payload(decode=True).decode('utf-8'))

    def test_with_token(self):
        message = email_queue.generate_invite(u'h\u00e9llo Name', 'from@example.com', 'to@example.com',
            u'H\u00e9llo service', '99token99')
        text_content = message.get_payload()[0].get_payload(decode=True).decode('utf-8')
        self.assertTrue('/99token99' in text_content)
        html_content = message.get_payload()[1].get_payload(decode=True).decode('utf-8')
        self.assertTrue('/99token99' in html_content)


class InviteNewUserTest(unittest.TestCase):
    def test_simple(self):
        to_address = 'to+&?special@example.com'
        password ='temp+&?#pass'
        email_queue.send_new_user_invite('from@example.com', to_address, password)
        message = email_queue._last_message

        self.assertEquals('from@example.com', message['Reply-To'])
        self.assertEquals(to_address, message['To'])
        self.assertTrue('charset="utf-8"' in message.get_payload()[0]['Content-Type'].lower())
        self.assertTrue('charset="utf-8"' in message.get_payload()[1]['Content-Type'].lower())

        # Verify that links actually go to the right URL (escaping bug ruined this once)
        html = message.get_payload()[1].get_payload(decode=True).decode('utf-8')
        count = 0
        HREF_RE = re.compile('href="([^"]+)">')
        for match in HREF_RE.finditer(html):
            count += 1
            url, hash = match.group(1).split('#')
            self.assertEquals('https://www.mitro.co/install.html', url)
            quoted = urllib.urlencode({'u': to_address, 'p': password})
            # HTML unquote
            hash = hash.replace('&amp;', '&')
            self.assertEquals(quoted, hash)
        self.assertTrue(count > 0)

        # Make sure text links are *not* escaped
        text = message.get_payload()[0].get_payload(decode=True).decode('utf-8')
        count = 0
        TEXT_RE = re.compile('(https://www.mitro.co/[^\s]+)')
        for match in TEXT_RE.finditer(text):
            count += 1
            quoted = urllib.urlencode({'u': to_address, 'p': password})
            self.assertEquals('https://www.mitro.co/install.html#' + quoted,
                match.group(1))
        self.assertEquals(1, count)



class VerifyDeviceTest(unittest.TestCase):
    def test_simple(self):
        to_address = 'to+&?special@example.com'
        token = 'token'
        signature = 'signature'
        email_queue.send_device_verification(to_address, token, signature)
        message = email_queue._last_message

        self.assertEquals(to_address, message['To'])

        # Verify that links go to the right URL
        html = message.get_payload()[1].get_payload(decode=True).decode('utf-8')
        self.assertTrue('https://www.mitro.co/mitro-core/user/VerifyDevice?' in html)


class SendAddressVerificationTest(unittest.TestCase):
    def test_simple(self):
        to_address = 'to@example.com'
        code = 'special+&?-@code'
        email_queue.send_address_verification(to_address, code)
        message = email_queue._last_message

        self.assertEquals(to_address, message['To'])

        # Verify that links actually go to the right URL (escaping bug ruined this once)
        html = message.get_payload()[1].get_payload(decode=True).decode('utf-8')
        count = 0
        HREF_RE = re.compile('href="([^"]+)">')
        for match in HREF_RE.finditer(html):
            count += 1
            url, hash = match.group(1).split('?')
            self.assertEquals('https://www.mitro.co/mitro-core/user/VerifyAccount', url)
            quoted = urllib.urlencode({'user': to_address, 'code': code})
            # HTML unquote
            hash = hash.replace('&amp;', '&')
            self.assertEquals(quoted, hash)

        self.assertEquals(2, count)


# TODO: Re-enable once templates are added
# class SendShareNotificationTest(unittest.TestCase):
#     def test_simple(self):
#        sender_name = 'Sender'
#        sender_email = 'sender@example.com'
#        recipient_name = 'Recipient'
#        recipient_email = 'recipient@example.com'
#        secret_title = 'Secret Title'
#        secret_url = 'http://www.example.com/'

#        email_queue.send_share_notification(sender_name, sender_email, recipient_name, recipient_email, secret_title, secret_url) 


class FakeStatsd(object):
    def __init__(self):
        self.buckets = {}

    def incr(self, bucket):
        count = self.buckets.get(bucket, 0)
        count += 1
        self.buckets[bucket] = count


class EmailQueueTest(tornado.testing.LogTrapTestCase):
    def setUp(self):
        self.db = server.db.Session()
        self.statsd = FakeStatsd()
        email_queue._fake_send_count = 0

    def test_simple(self):
        self.assertEquals(None, email_queue.poll_queue(self.db, models2.EmailQueue))
        # attempts to check that no transaction is active
        self.assertEquals(0, len(self.db.transaction._connections))

        queue_test_invite(self.db, u'h\u00e9llo Name', 'from@example.com', 'to@example.com',
            'service name')
        # attempts to check that queuing does not commit for us
        # TODO: abort instead and check that there are no entries; this no longer works
        # self.assertEquals(1, len(self.db.transaction._connections))
        self.db.commit()
        self.assertEquals(0, len(self.db.transaction._connections))

        invite = email_queue.poll_queue(self.db, models2.EmailQueue)
        self.assertEquals(0, len(self.db.transaction._connections))
        # reading the arguments should not start a transaction
        self.assertTrue(len(invite.arg_string) > 0)
        self.assertEquals(0, len(self.db.transaction._connections))

        self.assertEquals(None, email_queue.poll_queue(self.db, models2.EmailQueue))
        self.assertEquals(0, len(self.db.transaction._connections))

        email_queue.ack_queued_item(self.db, models2.EmailQueue, invite)
        self.assertEquals(0, len(self.db.transaction._connections))

        self.assertRaises(AssertionError, email_queue.ack_queued_item, self.db, models2.EmailQueue, invite)
        self.assertEquals(2, len(self.db.transaction._connections))
        self.db.close()

    def test_process(self):
        self.assertFalse(email_queue._loop_once(self.db, models2.EmailQueue, self.statsd))
        self.assertEquals(0, len(self.db.transaction._connections))
        self.assertEquals(1, self.statsd.buckets[email_queue._STATSD_POLLS])

        # queue and process an invitation
        queue_test_invite(self.db, u'h\u00e9llo Name', 'from@example.com', 'to@example.com',
            'service name')
        self.db.commit()

        self.assertTrue(email_queue._loop_once(self.db, models2.EmailQueue, self.statsd))
        self.assertEquals(1, email_queue._fake_send_count)
        self.assertEquals(0, len(self.db.transaction._connections))
        self.assertEquals(2, self.statsd.buckets[email_queue._STATSD_POLLS])
        self.assertEquals(1, self.statsd.buckets[email_queue._STATSD_DEQUEUED])
        self.assertEquals(1, self.statsd.buckets[email_queue._STATSD_SUCCESS])

        # queue and process an incorrect queue item to test error handling code
        self.db.add(models2.EmailQueue('bad_type', '["arg1", "arg2"]', None, None))
        self.db.commit()

        self.assertFalse(email_queue._loop_once(self.db, models2.EmailQueue, self.statsd))
        self.assertEquals(1, email_queue._fake_send_count)
        self.assertEquals(0, len(self.db.transaction._connections))
        self.assertFalse(email_queue._loop_once(self.db, models2.EmailQueue, self.statsd))
        self.assertEquals(0, len(self.db.transaction._connections))
        self.assertEquals(1, self.statsd.buckets[email_queue._STATSD_SUCCESS])
        self.assertEquals(1, self.statsd.buckets[email_queue._STATSD_FAILED])


class FakeTime(object):
    def __init__(self):
        self.now = 1374859941
        self.slept_seconds = None

    def time(self):
        return self.now

    def sleep(self, seconds):
        self.slept_seconds = seconds


class BackoffSleeperTest(unittest.TestCase):
    def testSimple(self):
        timer = FakeTime()
        sleeper = email_queue.BackoffSleeper(timer)

        for sleep_seconds in email_queue.BackoffSleeper._SLEEP_SECONDS:
            self.assertTrue(sleeper.shouldRetryAfterSleep())
            self.assertEquals(sleep_seconds, timer.slept_seconds)
            timer.slept_seconds = None

        # Retries expired: return false and don't sleep
        self.assertFalse(sleeper.shouldRetryAfterSleep())
        self.assertFalse(sleeper.shouldRetryAfterSleep())
        self.assertEquals(None, timer.slept_seconds)

        # If we wait long enough, the sleep resets (e.g. if it successfully runs for a while)
        timer.now += email_queue.BackoffSleeper._RESET_SECONDS
        self.assertTrue(sleeper.shouldRetryAfterSleep())
        self.assertEquals(email_queue.BackoffSleeper._SLEEP_SECONDS[0], timer.slept_seconds)


if __name__ == "__main__":
    server.db.connect_to_test_database()
    models2.Base.metadata.create_all(server.db.get_engine())
    unittest.main()
