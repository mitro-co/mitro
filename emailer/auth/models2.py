#!/usr/bin/env python

# import datetime
import json
# import logging

# import sqlalchemy
# import sqlalchemy.exc
import sqlalchemy.ext.declarative
# import sqlalchemy.orm
# from sqlalchemy.pool import StaticPool
# from sqlalchemy.pool import NullPool

# import tornado.options

# from auth import email_queue
# from auth import statsd

Base = sqlalchemy.ext.declarative.declarative_base()

class EmailQueue(Base):
    '''Queues emails to be sent by another process.'''
    # TODO: Use Kombu queuing?
    __tablename__ = 'email_queue'

    id = sqlalchemy.Column(sqlalchemy.Integer, primary_key=True, autoincrement=True, nullable=False)
    type_string = sqlalchemy.Column(sqlalchemy.Text, nullable=False)
    arg_string = sqlalchemy.Column(sqlalchemy.Text, nullable=False)
    attempted_time = sqlalchemy.Column(sqlalchemy.DateTime)
    template_name = sqlalchemy.Column('mandrill_template_name', sqlalchemy.Text, nullable=True)
    template_params_string = sqlalchemy.Column(
            'mandrill_template_param_map_json',sqlalchemy.Text, nullable=True)

    def __init__(self, type_string, arg_string, template_name,
                 template_params_string):
        self.type_string = type_string
        # arg_string must be valid json
        self.arg_string = arg_string
        assert len(self.get_arguments()) > 0

        self.template_name = template_name
        self.template_params_string = template_params_string

    def get_arguments(self):
        return json.loads(self.arg_string)

    def get_template_params(self):
        return json.loads(self.template_params_string)
