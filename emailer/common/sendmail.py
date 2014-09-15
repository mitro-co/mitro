import cStringIO
import smtplib
import email.Charset
import email.generator
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from tornado import options

options.define('smtp_user', '', help='User for SMTP (currently unused)')
options.define('smtp_password', '', help='Password for SMTP (currently unused)')
options.define('smtp_host', 'email-smtp.us-east-1.amazonaws.com', help='Host for SMTP (currently unused)')
options.define('smtp_port', '2587', help='Port for SMTP (currently unused)')
# For Mandrill: host = 'smtp.mandrillapp.com' port = 587


# From: http://radix.twistedmatrix.com/2010/07/how-to-send-good-unicode-email-with.html
# Override python's weird assumption that utf-8 text should be encoded with
# base64, and instead use quoted-printable (for both subject and body).
email.Charset.add_charset('utf-8', email.Charset.QP, email.Charset.QP, 'utf-8')


def make_email_message(html_string, subject, to, frm, 
                    text_string='It\'s 2012! Get a mail client that displays HTML: http://www.gmail.com'):
    message = MIMEMultipart('alternative')

    text_part = MIMEText(text_string, 'plain', 'UTF-8')
    if not html_string:
        # No HTML: Create a single part message with the text body
        message = text_part

    message['Subject'] = subject
    # Encode as us-ascii: ensures unicode strings do not contain special characters
    # TODO: Support senders/recipients with unicode names
    frm = frm.encode('us-ascii')
    assert type(frm) == str, type(frm)
    message['From'] = frm
    to = to.encode('us-ascii')
    assert type(to) == str
    message['To'] = to

    if html_string:
        # The message is multi-part, with HTML and text
        html_part = MIMEText(html_string, 'html', 'UTF-8')
        # most important at end
        message.attach(text_part)
        message.attach(html_part)

    return message


def to_string(message):
    # The default email Message as_string escapes From lines, in case it is
    # used in a Unix mbox format:
    # http://homepage.ntlworld.com./jonathan.deboynepollard/FGA/mail-mbox-formats.html
    io = cStringIO.StringIO()
    g = email.generator.Generator(io, False)  # second arg: "should I mangle From?"
    g.flatten(message)
    return io.getvalue()

    
def send_message_via_smtp(message, host='localhost', port=25,
                          user=None, pwd=None):
    if (user or pwd): assert (user and pwd)
    if type(message) != list:
        messages = [message]
    else: 
        messages = message
    if user:
        s = smtplib.SMTP(host, port)
        s.starttls()
        s.login(user,pwd)
    else:
        s = smtplib.SMTP(host, port)
    for message in messages:
        s.sendmail(message['from'], message['to'], to_string(message))
    s.quit()
    
def send_message_via_smtp_options(message):
    ''' Send a message via SMTP using settings in command line flags.
    message can be a child of email.Message or a list 
    of those which will all be sent
    ''' 
    return send_message_via_smtp(message, options.options.smtp_host, options.smtp_port,
        options.options.smtp_host, options.options.smtp_password)
