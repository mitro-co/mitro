from tornado.options import define, options

from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from sqlalchemy.pool import NullPool

define('mysql_socket', None, type=str, help='Path to local MySQL socket')
define('mysql_hostname', 'localhost', type=str, help='Hostname of the MySQL server')
define('db_name', 'lectorius', help='Name of MySQL database')

TEST_DATABASE_FN = '/tmp/lectorius.db'
TEST_DATABASE = 'sqlite:///' + TEST_DATABASE_FN

Base = declarative_base()
Session = sessionmaker()

engine = None

def getDatabaseUrl():
    # charset parameter forces us to pass MySQL data in UTF-8, ignoring MySQL's config
    url = 'mysql://%s:%s@%s/%s?charset=utf8mb4' % (MYSQL_USERNAME,
        MYSQL_PASSWORD, options.mysql_hostname, options.db_name)
    if options.mysql_socket:
        url += '&unix_socket=' + options.mysql_socket
    return url

def get_engine():
    return engine

def connect_to_database():
    '''Connects SQLAlchemy to the database. Call this after command-line parsing.'''

    global engine
    engine = create_engine(getDatabaseUrl(),
                           poolclass=NullPool, echo=False)
    Session.configure(bind=engine)

def connect_to_test_database():
    '''Connects SQLAlchemy to the test database. Call this after command-line
    parsing.'''

    global engine

    # We need to use the StaticPool so everything shares the in-memory DB
    engine = create_engine('sqlite://',
                           poolclass=StaticPool)
    Session.configure(bind=engine)
