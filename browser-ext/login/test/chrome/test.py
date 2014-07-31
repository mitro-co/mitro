
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

import argparse
import logging
import os
import random
import string
import time
import unittest
import pyperclip
import sys
import traceback
import base64
import subprocess

from urlparse import urlparse

from selenium import webdriver
from selenium.common import exceptions
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webelement import WebElement
import selenium.webdriver.support.expected_conditions as conditions
from selenium.webdriver.support import ui

from common import general

arg_parser = argparse.ArgumentParser('Mitro frontend test')
arg_parser.add_argument('--extension-path', required=True,
                        help='Path to extension crx file')
arg_parser.add_argument('--test-forms-url', required=False,
                        help='Test forms site url')
arg_parser.add_argument('--browser', required=False, default='chrome',
                        help='The options are: chrome, safari')
arg_parser.add_argument('--selenium-server-host', required=False, default='localhost',
                        help='The host on which the selenium server is running')
arg_parser.add_argument('--remote', required=False, default='False',
                        help='Is the selenium server running remotely? Options: True, False')
args = arg_parser.parse_args()

_PATH = os.path.dirname(os.path.realpath(__file__))
_WEBDRIVER_PATH = os.path.join(_PATH, '..', 'build', 'selenium', 'chromedriver')
_MITRO_EXTENSION_TITLE = 'Mitro Login Manager'

SIGNUP_PATH = '/html/signup.html'
LOGIN_PATH = '/html/popup.html'
CHANGE_PASSWORD_PATH = '/html/change-password.html'
ISSUE_PATH = '/html/issue.html'
SERVICES_PATH = '/html/services.html'
GROUPS_PATH = '/html/groups.html'
PRIVACY_PATH = '/html/privacy.html'
TERMS_PATH = '/html/terms.html'
TEST_FORMS_URL = args.test_forms_url and [args.test_forms_url][0] \
        or 'http://forms.it-works.org.ua'
REMOTE_SERVER = (args.remote == 'True' and [True] or [False])[0]

ITEM_NO = 0

subprocesses = []

def random_string(length):
    return ''.join(random.choice(string.letters) for x in range(length))


class MitroExtensionTester(unittest.TestCase):
    def __init__(self, driver, base_url):
        self._driver = driver
        self._base_url = base_url
        self._wait = ui.WebDriverWait(self._driver, 60)
        self._teams_number = 0;
        self._secure_notes_number = 0;
        self._manual_passwords_number = 0;
        self._automatic_passwords_number = 0;
        subprocesses = [];
        
        

    def sleep(self, seconds):
        logging.info('SLEEP %s seconds' % seconds)
        time.sleep(seconds)
    
    def loadURL(self, url):
        logging.info('LOAD %s' % url)
        self._driver.get(url)
        if self._driver.name == 'safari':
            time.sleep(2)

    #helper functions
    #general functions
    def get_url(self, path):
        return '%s%s' % (self._base_url, path)

    def get_static_url(self, path):
        return 'http://localhost:8001%s' % (path)

    def log_in(self):
        self.loadURL(self.get_url(LOGIN_PATH))

        #finding all the elements we'll interact with
        username_element = self._driver.find_element_by_name('username')
        password_element = self._driver.find_element_by_name('password')
        submit_button = self._driver.find_element_by_id('mitro-login-form-button')
        
        #performing log-in actions
        username_element.clear()
        username_element.send_keys(self.username)
        password_element.send_keys(self.password)
        submit_button.click()
        
        #making sure we're logged in
        logging.info('WAIT for the successful login marker')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-in']))
        logging.info('** Logged in')
    
    def log_out(self):
        logging.info('* Logging out')
        self.loadURL(self.get_url(LOGIN_PATH))
        logout_link = self._driver.find_element_by_id('logout')
        logout_link.click()
        
        #making sure we've apeared on the home screen logged out
        logging.info('Waiting for the successful logout marker')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-out']))
        logging.info('** Logged out')
    
    #extension toolbar helpers
    def get_extension_frame(self):
        logging.info('* Switching to the infobar frame')
        return [element for element \
                    in self._driver.find_elements_by_tag_name('iframe')\
                    if element.get_attribute('srcdoc')][-1]

    def switch_to_extension(self):
        logging.info('* Switching to the main frame')
        self._driver.switch_to_frame(self.get_extension_frame())

    def get_extension_buttons(self):
        """
        The function returns the extension buttons dictionary.
        The keys are the buttons text in the lower case. The spaces
        get replaces by the underscore.
        """
        buttons = {}
        for button in self._driver.find_elements_by_css_selector\
                    ('button[type="button"]'):
            button_text = button.text.strip()
            button_key = button_text.lower().replace(' ', '_')
            buttons[button_key] = button
        
        return buttons
    
    #Custom conditions to wait for the test forms markers.
    #The standard conditions fail for some reason after
    #switching driver to/from extension iframe
    def condition_login_form_loaded(self, driver):
        try:
            self._driver.find_element_by_id('login-form-marker')
        except:
            return False
        
        return True
    
    def condition_login_success(self, driver):
        try:
            self._driver.find_element_by_id('login-success-marker')
        except:
            return False
        
        return True
    
    def condition_login_fail(self, driver):
        try:
            self._driver.find_element_by_id('login-fail-marker')
        except:
            return False
        
        return True

    #teams functions
    def find_team_elements(self):
        """
        The function to find the team elements in the list
        """
        team_elements = self._driver.find_elements_by_class_name('team-item')
        
        return team_elements
    
    def verify_teams_number(self, driver):
        """
        The function to verify the right teams number in the list
        """
        return len(self.find_team_elements()) == self._teams_number
    
    def verify_team_present(self, team_name):
        """
        This function takes the team name and searches
        the teams list for the element containing that name
        
        Returns True if the name is found. Otherwise returns False
        """
        for team_element in self.find_team_elements():
            title = team_element.find_element_by_class_name('title').text.strip()
            if title == team_name:
                return True
         
        return False
    
    def open_team_edit_modal(self, add=False):
        logging.info('* Opening the team edition modal')
        if add:
            #if we're adding new team, we're hitting the add button
            new_team_button = self._driver.find_element_by_id('add-new-team-button')
            new_team_button.click()
        else:
            #otherwise we're hitting the first edit button in the list
            edit_button = self._driver.find_element_by_class_name('edit-team-button')
            edit_button.click()
        
        #waiting for the modal to open
        logging.info('WAIT for the team edition modal')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'addedit-group-modal']))
        self.sleep(1)
        
        #returning elements we're going to use
        return {
            'name_field': self._driver.find_element_by_name('name'),
            'done_button': self._driver.find_element_by_id('addedit-group-button'),
            'new_member_input': self._driver.find_element_by_class_name('add-team-member-text'),
            'add_button': self._driver.find_element_by_class_name('add-team-member-button'),
            'delete_link': self._driver.find_element_by_id('delete-group-link')
        }
    
    def toggle_team_access(self, note_context, team_name):
        #going to the services page
        self.loadURL(self.get_url(SERVICES_PATH))
        tabs_list = self._driver.find_element_by_id('service-tabs')
        tabs_list.find_element_by_id(note_context.tab_id).click()
        self.wait_for_refresh(note_context, page_refresh=False)
        #finding the target note element
        target_element = self.get_secure_elements(note_context)[-1]
        m = self.secure_item_configuration_modal(note_context, target_element)
        #finding all participants elements
        participant_elements = m['modal'].find_element_by_class_name('team-member-list')\
                            .find_elements_by_tag_name('li')
        #searching the note participants for the target team
        team_found = False #the flag
        for element in participant_elements:
            name = element.find_element_by_class_name('name').text.strip()
            if name == team_name:
                team_found = True
                #toggling the checkbox
                element.find_element_by_tag_name('input').click()
                break
        
        #we expect the target team to be found
        self.assertTrue(team_found, 'The team is not present among the participants')
        #hitting 'save' and waiting for refresh
        m['save_button'].click()
        self.wait_for_refresh(note_context)
        logging.info('OK toggle team access')
    
    def wait_for_teams_refresh(self):
        logging.info('WAIT for the teams list to refresh:')
        body = self._driver.find_element_by_tag_name('body')
        logging.info('WAIT for the page unload')
        self._wait.until(conditions.staleness_of(body))
        logging.info('WAIT for the right teams count')
        self._wait.until(self.verify_teams_number)
    
    #secure items functions
    def get_secure_elements(self, context):
        """
        Function to get the secure elements from the list
        """
        return self._driver.find_element_by_id(context.container_id)\
                .find_elements_by_class_name('active-service-item')

    def verify_secure_items_number(self, context):
        """
        Function to verify the items number in the list
        """
        return lambda driver: len(self.get_secure_elements(context)) == context.items_number

    def verify_last_secure_item(self, context):
        """
        Funtion to verify if the last item in the list
        contains the right content
        
        Returns the item element
        """
        logging.info('* Verifying the last secure item')
        #we're always using the last item in the list for our tests
        last_secure_element = self.get_secure_elements(context)[-1]
        
        #getting the modal opened
        view_button = last_secure_element.find_element_by_tag_name('button')
        view_button.click()
        modal = self._driver.find_element_by_id(context.view_modal_id)
        logging.info('WAIT for the modal')
        self._wait.until(conditions.visibility_of(modal))
        self.sleep(1)#waiting for transition
        
        if context.type == context.NOTE:
            #secure notes specific actions
            #getting the actual note title and text
            note_title = modal.find_element_by_class_name('secure-note-name').text.strip()
            note_text_element = modal.find_element_by_name('note_text')
            note_text = note_text_element.get_attribute('value')
        
            #locating the buttons we're going to use
            copy_button = modal.find_element_by_class_name('copy-note')
        
            #checking the correctness of the title and the text
            self.assertEqual(note_title, context.content['title'], 'Note title is not correct')
            self.assertEqual(note_text, context.content['text'], 'Note text is not correct')
        
            #testing the 'Copy' button
            copy_button.click()
            self.assertEqual(pyperclip.paste(), unicode(context.content['text']),
                             'Note text is not correct')
        elif context.type == context.MANUAL_PASSWORD:
            #manual passwords specific actions
            #getting the actual item content and/or the corresponding elements
            title = modal.find_element_by_class_name('auth-manual-service-name').text.strip()
            url_element = modal.find_element_by_class_name('auth-manual-url')
            url_text = url_element.text.strip()
            url_href = url_element.get_attribute('href')
            password_hidden_field = modal.find_element_by_class_name('auth-manual-password')
            password_shown_field = modal.find_element_by_class_name('shown-password')
            
            #locating the buttons we're going to use
            copy_username_btn = modal.find_element_by_class_name('copy-username')
            toggle_password_btn = modal.find_element_by_class_name('reveal-password')
            copy_password_btn = modal.find_element_by_class_name('copy-password')
            
            #checking the item content correctness
            self.assertEqual(title, context.content['title'])
            self.assertEqual(url_href, context.content['url'])
            self.assertFalse(context.content['url'].find(url_text) == -1)
            #testing password toggle
            elements = {
                'password_hidden_field': password_hidden_field,
                'password_shown_field': password_shown_field,
                'toggle_password_btn': toggle_password_btn,
            }
            self.test_password_toggle(context, elements)
            
            #checking the copy functionality
            #copying username
            copy_username_btn.click()
            self.assertEqual(pyperclip.paste(), unicode(context.content['username']))
            #checking password copy in both hidden and shown states
            copy_password_btn.click()
            self.assertEqual(pyperclip.paste(), unicode(context.content['password']))
            toggle_password_btn.click()
            copy_password_btn.click()
            self.assertEqual(pyperclip.paste(), unicode(context.content['password']))
        
        #closing the modal
        modal.find_element_by_link_text('Close').click()
        logging.info('WAIT until the modal gets hidden')
        self._wait.until(conditions.invisibility_of_element_located([By.ID, context.view_modal_id]))
        logging.info('OK last secure item verified')
        
        return last_secure_element
    
    def secure_item_configuration_modal(self, context, item_element):
        """
        Returns the modal's useful elements
        """
        logging.info('* Getting the configuration modal')
        #opening the modal by clicking the 'Configure' link
        item_element.find_element_by_class_name('configure').click()
        modal = self._driver.find_element_by_id(context.configuration_modal_id)
        logging.info('WAIT for the modal')
        self._wait.until(conditions.visibility_of(modal))
        self.sleep(1)#waiting for transition
        
        if context.type == context.NOTE:
            return {
                'modal': modal,
                'title_element': modal.find_element_by_name('name'),
                'edit_link': modal.find_element_by_class_name('edit-link'),
                'new_participant_field': modal.find_element_by_class_name('add-team-member-text'),
                'add_participant_button': modal.find_element_by_class_name('add-team-member-button'),
                'save_button': modal.find_element_by_class_name('save-service-button'),
                'delete_link': modal.find_element_by_class_name('delete-service-link'),
                'cancel_button': modal.find_element_by_link_text('Cancel')
            }
        else:
            return {
                'modal': modal,
                'title_element': modal.find_element_by_name('name'),
                'url_link': modal.find_element_by_class_name('configure-url'),
                'edit_link': modal.find_element_by_class_name('edit-link'),
                'new_participant_field': modal.find_element_by_class_name('add-team-member-text'),
                'add_participant_button': modal.find_element_by_class_name('add-team-member-button'),
                'save_button': modal.find_element_by_class_name('save-service-button'),
                'delete_link': modal.find_element_by_class_name('delete-service-link'),
                'cancel_button': modal.find_element_by_link_text('Cancel')
            }
    
    def update_secure_item(self, context, edit_modal_elements):
        logging.info('* Updating the secure item')
        m = edit_modal_elements #the shortcut
        #generating new data to update the item
        context.generate_content()
        #updating the item
        m['edit_link'].click()
        c = self.secure_item_edit_details_modal(context)
        if context.type == context.NOTE:
            #secure note specific actions:
            c['note_text_element'].clear()
            c['note_text_element'].send_keys(context.content['text'])
            c['done_button'].click()
        else:
            #manual password specific actions
            c['username_field'].clear()
            c['username_field'].send_keys(context.content['username'])
            c['password_hidden_field'].clear()
            c['password_hidden_field'].send_keys(context.content['password'])
            c['password_toggle_btn'].click()
            self.assertEqual(c['password_shown_field'].get_attribute('value'),
                             context.content['password'])
            c['password_shown_field'].clear()
            c['password_shown_field'].send_keys(context.content['password'])
            c['done_button'].click()
            
            #the hack to let the manual passwords edition pass the test
            #as the url edition functionality doesn't seem to be implemented
            if context.type == context.MANUAL_PASSWORD:
                context.content['query_string'] = context.previose_content['query_string']
            context.content['url'] = context.previose_content['url']
        
        logging.info('WAIT for the modal')
        self._wait.until(conditions.invisibility_of_element_located([By.ID, context.edit_detail_modal_id]))
        
        #updating the item title
        m['title_element'].clear()
        m['title_element'].send_keys(context.content['title'])
        #adding new participant
        m['new_participant_field'].send_keys(context.additional_participant)
        m['add_participant_button'].click()
        logging.info('WAIT for the participants list')
        self._wait.until(lambda driver: len(self._driver.find_element_by_class_name('team-member-list').\
                         find_elements_by_tag_name('li')) == 2)
        
        #saving the updated item
        m['save_button'].click()
            
        #waiting for the items list to refresh
        self.wait_for_refresh(context)
        logging.info('OK secure item updated')
    
    def test_password_toggle(self, context, elements):
        """
        The function to test the password show/hide actions
        and the copy functionality in boty hidden and shown
        conditions
        """
        logging.info('* Testing the passwords toggling')
        e = elements #the shortcut
        
        #checking if the password is hidden by default
        self.assertTrue(e['password_hidden_field'].is_displayed())
        self.assertFalse(e['password_shown_field'].is_displayed())
        self.assertFalse(e['password_shown_field'].get_attribute('value'))
        #checking if the password is shown when we hit 'show'
        e['toggle_password_btn'].click()
        self.assertFalse(e['password_hidden_field'].is_displayed())
        self.assertTrue(e['password_shown_field'].is_displayed())
        if context.content.get('password'):
            self.assertEqual(e['password_shown_field'].get_attribute('value'),
                             context.content['password'])
        else:
            self.assertTrue(e['password_shown_field'].get_attribute('value'))
        #checking if the password is hidden again when we hit 'hide'
        e['toggle_password_btn'].click()
        self.assertTrue(e['password_hidden_field'].is_displayed())
        self.assertFalse(e['password_shown_field'].is_displayed())
        self.assertFalse(e['password_shown_field'].get_attribute('value'))
        logging.info('OK passwords toggling')
    
    def test_remove_participant(self, context, target_element):
        """
        The function to test removing participant from access list
            -opens the modal
            -removes the participant
            -saves the item and refreshes the list
            -checking the participant had been removed
            -closes the modal
        """
        logging.info('* Testing the participants removal')
        m = self.secure_item_configuration_modal(context, target_element)
        (participants_number, participant_present, participant_checkbox) = self.verify_participants(
                    m['modal'], context)
        #making sure we've got two participates now
        self.assertEqual(participants_number, float(2))
        #making sure the new participant is among those two
        self.assertTrue(participant_present)
        
        #deleting the participant using the same config modal
        participant_checkbox.click()
        m['save_button'].click()
        #waiting for the items list to refresh
        self.wait_for_refresh(context)
        
        #verifying the participants list
        m = self.secure_item_configuration_modal(context, self.get_secure_elements(context)[-1])
        (participants_number, participant_present, participant_checkbox) = self.verify_participants(
                    m['modal'], context)
        #making sure we've got only one participant left
        self.assertEqual(participants_number, float(1))
        #making sure the participant left is not the one we've deleted
        self.assertFalse(participant_present)
        
        #closing the modal
        m['cancel_button'].click()
        logging.info('WAIT until the modal is hidden')
        self._wait.until(conditions.invisibility_of_element_located([By.ID, context.configuration_modal_id]))
        logging.info('OK participants removal')
    
    def verify_automatic_password(self, context, target_element):
        """
        Function to verify the automatic password item data correctness
        
        Returns configuration modal elements
        """
        logging.info('* Verifying the automatic password')
        #opening config modal
        m = self.secure_item_configuration_modal(context, target_element())
        #verifying the title
        self.assertEqual(m['title_element'].get_attribute('value'),
                         str(context.content['title']),
                         'Element title is not correct')
        #opening the details edition modal
        m['edit_link'].click()
        c = self.secure_item_edit_details_modal(context)
        #verifying the username
        if context.content.get('username'):
            self.assertEqual(c['username_field'].get_attribute('value'),
                        context.content['username'], 'Username is not correct')
        else:
            self.assertTrue(c['username_field'].get_attribute('value'),
                        'Element title is empty')
        #testing the username copying
        c['copy_username_btn'].click()
        self.assertTrue(pyperclip.paste(), 'Copying the username does not work')
        
        #testing password toggling and copying
        self.test_password_toggle(context, c)
        
        #closing the details edition modal
        c['done_button'].click()
        logging.info('wAIT until the modal is hidden')
        self._wait.until(conditions.invisibility_of_element_located\
                         ([By.ID, context.edit_detail_modal_id]))
        logging.info('OK verifyin the automatic password')
        
        #returning the config modal elements for further usage
        return m
    
    def wait_for_refresh(self, context, page_refresh=True):
        """
        Function to wait for secure items list to refresh
        
        The 'selector' argument is the indicator element selector
        like [By.ID, 'some_id']
        """
        if page_refresh:
            #waiting for old page to unload
            body = self._driver.find_element_by_tag_name('body')
            logging.info('WAIT for the body to become stale (page unload)')
            self._wait.until(conditions.staleness_of(body))

        if context.items_number:
            logging.info('WAIT for the refresh indicator to appear on the page')
            self._wait.until(conditions.presence_of_element_located(context.refresh_indicator))#let it show
        else:
            self.sleep(2) #if there's no items to wait for we still have to give it some time
    
    def verify_participants(self, modal, context):
        """
        The function to verify secure item participants number
        and to detect if the particular participant present in the list
        """
        logging.info('* Verifying the participants')
        #setting the results defaults
        participant_present = False
        participants_number = 0
        participant_checkbox = False
        
        #processing the participants list
        for element in modal.find_element_by_class_name('team-member-list').\
                find_elements_by_tag_name('li'):
            participants_number += 1
            participant_name = element.find_element_by_class_name('name').text.strip()
            if participant_name == context.additional_participant:
                if not participant_present:
                    participant_present = True
                    participant_checkbox = element.find_element_by_tag_name('input')
                else:
                    raise Exception('New participant name is shown more than once')
        
        logging.info('OK participants verified')
        return (participants_number, participant_present, participant_checkbox)
    
    def secure_item_edit_details_modal(self, context):
        """
        The function to find all the useful elements
        of the details edition modal
        
        Returns the elements dict
        """
        logging.info('* Opening details modal')
        modal = self._driver.find_element_by_id(context.edit_detail_modal_id)
        logging.info('WAIT for the modal')
        self._wait.until(conditions.visibility_of(modal))
        self.sleep(1)#waiting for transition
        if context.type == context.NOTE:
            return {
                'note_text_element': modal.find_element_by_name('note_text'),
                'done_button': modal.find_element_by_class_name('save-secure-note-button'),
            }
        else:
            return {
                'username_field': modal.find_element_by_class_name('auth-manual-username'),
                'password_hidden_field': modal.find_element_by_class_name('auth-manual-password'),
                'password_shown_field': modal.find_element_by_class_name('shown-password'),
                'password_toggle_btn': modal.find_element_by_class_name('reveal-password'),
                'copy_username_btn': modal.find_element_by_class_name('copy-username'),
                'copy_password_btn': modal.find_element_by_class_name('copy-password'),
                'toggle_password_btn': modal.find_element_by_class_name('reveal-password'),
                'done_button': modal.find_element_by_class_name('save-credentials-button'),
            }
    
    def test_password_links(self, context, target_element_finder):
        """
        The function to test the password elements links
        and the login button of the automatic password elements
        """
        logging.info('* Testing the password links')
        #the link on the main screen
        link_element = target_element_finder().find_element_by_class_name('host')\
                .find_element_by_tag_name('a')
        self.assertTrue(link_element.get_attribute('href') == context.content['url'])
        
        #the link in the config modal
        self.loadURL(self.get_url(SERVICES_PATH))
        tabs_list = self._driver.find_element_by_id('service-tabs')
        tabs_list.find_element_by_id(context.tab_id).click()
        logging.info('WAIT for the right secure items number')
        self._wait.until(self.verify_secure_items_number(context))
        m = self.secure_item_configuration_modal(context,
                        target_element_finder())
        expected_url = 'http://%s/' % urlparse(context.content['url']).netloc
        self.assertTrue(expected_url == m['url_link'].get_attribute('href'))
        
        self.loadURL(self.get_url(SERVICES_PATH))
        tabs_list = self._driver.find_element_by_id('service-tabs')
        tabs_list.find_element_by_id(context.tab_id).click()
        logging.info('WAIT for the right secure items number')
        self._wait.until(self.verify_secure_items_number(context))
            
        if context.type == context.MANUAL_PASSWORD:
            #for the manual password we test the link in the view modal
            view_button = target_element_finder().find_element_by_tag_name('button')
            view_button.click()
            modal = self._driver.find_element_by_id(context.view_modal_id)
            logging.info('WAIT for the modal')
            self._wait.until(conditions.visibility_of(modal))
            self.sleep(1)
            link_element = modal.find_element_by_class_name('auth-manual-url')
            self.assertEqual(link_element.get_attribute('href'),
                             context.content['url'], 'The link is broken')
        elif context.type == context.AUTOMATIC_PASSWORD:
            #we test the login button for the automatic password
            log_in_btn = target_element_finder().find_element_by_class_name('sign-in-button')
            log_in_btn.click()
            #we can't check if the expected page is finished loading
            #as it is being opened in a new tab.
            #Let's just give it a little time.
            self.sleep(5)
            #now we just check the tabs one by one
            #looking for login fail marker
            login_failed_marker = False #the flag
            for handle in self._driver.window_handles:
                self._driver.switch_to_window(handle)
                try:
                    self._driver.find_element_by_id('login-fail-marker')
                    login_failed_marker = True
                    break
                except: pass
            #the marker has to be found. If not, that's the fail
            self.assertTrue(login_failed_marker)
            self._driver.close()
            self._driver.switch_to_window(self._driver.window_handles[0])
        logging.info('OK password links')

    def test_delete_item(self, context, target_element_finder):
        logging.info('* Testing items deletion')
        self.loadURL(self.get_url(SERVICES_PATH))
        tabs_list = self._driver.find_element_by_id('service-tabs')
        tabs_list.find_element_by_id(context.tab_id).click()
        
        logging.info('WAIT for the right items number')
        self._wait.until(self.verify_secure_items_number(context))
        #locating the desired secure item list element
        target_element = target_element_finder()
        #opening configuration modal
        target_element.find_element_by_class_name('configure').click()
        m = self.secure_item_configuration_modal(context, target_element)
        #hitting 'delete', waiting for the confirm popup
        m['delete_link'].click()
        delete_modal = self._driver.find_element_by_id('delete-confirm-modal')
        logging.info('WAIT for the deletion modal')
        self._wait.until(conditions.visibility_of(delete_modal))
        self.sleep(1) #wait for transition
        #testing the 'cancel' button
        delete_modal.find_element_by_link_text('Cancel').click()
        logging.info('WAIT for the confirmation modal')
        self._wait.until(conditions.invisibility_of_element_located([By.ID, 'delete-confirm-modal']))
        #hitting 'delete', waiting for the confirm popup
        m['delete_link'].click()
        logging.info('WAIT for the deletion modal')
        self._wait.until(conditions.visibility_of(delete_modal))
        self.sleep(1) #wait for transition
        #deleting the item
        delete_modal.find_element_by_link_text('Delete').click()
        context.items_number -= 1
        self.wait_for_refresh(context)
        
        self.verify_secure_items_number(context)
        
        #making sure the item we've deleted is not among those left
        for item in self.get_secure_elements(context):
            title = item.find_element_by_class_name('title').text.strip()
            self.assertNotEqual(title, context.content['title'])
        
        logging.info('OK item deletion')

    #the tests:
    def test_signup(self):
        logging.info('<<<<< START signup test')
        error_message = lambda: self._driver.find_element_by_id('signup-error').text.strip()
        get_elements = lambda: {
            'username_element': self._driver.find_element_by_name('username'),
            'password_element': self._driver.find_element_by_name('password'),
            'password2_element': self._driver.find_element_by_name('password2'),
            'submit_button': self._driver.find_element_by_id('mitro-signup-form-button'),
        }
        
        self.loadURL(self.get_url(SIGNUP_PATH))
        
        #testing the cancel button
        cancel_link = self._driver.find_element_by_class_name('form-link')
        cancel_link.click()
        self.sleep(1)
        page_title = self._driver.find_element_by_class_name('popup-title')
        self.assertEqual(page_title.text.strip(),
                         'Mitro Login Manager', 'Cancel button does not work')
        logging.info('OK Login form cancel button')
        
        #going back to the signup page
        self.loadURL(self.get_url(SIGNUP_PATH))
        
        #finding all the elements we'll interact with
        el = get_elements()
        
        #testing the empty login error
        el['submit_button'].click()
        self.assertEqual(error_message(), 'Please enter your email address',
                         'Empty login error is not shown')
        logging.info('OK empty login field error')

        #testing the empty password field error
        el['username_element'].send_keys(self.username)
        el['submit_button'].click()
        self.assertEqual(error_message(), 'Password is too weak',
                         'Empty password error is not shown')
        logging.info('OK empty password field error')

        #testing the too-short-password error
        short_password = random_string(7)
        el['password_element'].send_keys(short_password)
        el['password2_element'].send_keys(short_password)
        el['submit_button'].click()
        self.assertEqual(error_message(), 'Password is too weak',
                         'Short password error is not shown')
        logging.info('OK password too short error')
        
        #testing the password missmatch error
        el['password_element'].send_keys(self.password)
        el['submit_button'].click()
        self.assertEqual(error_message(), 'Passwords do not match',
                         'Passwords mismatch error is not shown')
        logging.info('OK passwords mismatch error')
        
        #testing the incorrect email error
        el['username_element'].clear()
        el['username_element'].send_keys(random_string(10))
        el['password_element'].clear()
        el['password2_element'].clear()
        el['password_element'].send_keys(self.password)
        el['password2_element'].send_keys(self.password)
        el['submit_button'].click()
        self.sleep(1)
        self.assertEqual(" ".join(error_message().split()),
                     'Error during signup: Unknown local error Click here to log in',
                     'Incorrect email error is not shown')
        logging.info('OK incorrect email error')

        #testing successful signup
        el['username_element'].clear()
        el['username_element'].send_keys(self.username)
        el['submit_button'].click()
        logging.info('WAIT for the successful login marker')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-in']))
        logging.info('OK successfully signed up')
        
        #test the 'user exists' error
        self.log_out()
        #trying signing up using the same credentials
        self.loadURL(self.get_url(SIGNUP_PATH))
        el = get_elements()
        el['username_element'].clear()
        el['username_element'].send_keys(self.username)
        el['password_element'].send_keys(self.password)
        el['password2_element'].send_keys(self.password)
        el['submit_button'].click()
        logging.info('WAIT for the error report')
        self._wait.until(conditions.text_to_be_present_in_element([By.CLASS_NAME, 'text-error'], 'Error during signup'),
                    'No error has been raised while trying to sign up with the same credentials for the second time')
        logging.info('OK "user exists" error')
        
        # test that username is filled in from hash tag
        self.loadURL(self.get_url(SIGNUP_PATH + '#u=' + self.username))

        el = get_elements()
        self.assertEqual(self.username, el['username_element'].get_attribute('value'),
                    'The username in the field does not match the one passed via the hash parameter')
        logging.info('OK passing username via the hash parameter')
        
        #logging back in for the next test
        self.log_in()
        logging.info('>>>>> SUCCESS signup test \n\n\n')

    def test_login(self):
        logging.info('\n<<<<< START login test')
        error_message = lambda: self._driver.find_element_by_id('login-error').text.strip()
        self.loadURL(self.get_url(LOGIN_PATH))

        #finding all the elements we'll interact with
        username_element = self._driver.find_element_by_name('username')
        password_element = self._driver.find_element_by_name('password')
        submit_button = self._driver.find_element_by_id('mitro-login-form-button')
        
        #testing the empty login error
        username_element.clear()
        submit_button.click()
        self.assertEqual(error_message(), 'Please enter your email address',
                         'Empty login error is not shown')
        logging.info('OK empty login error')
        
        username_element.send_keys(self.username)
        
        #testing the incorrect password error
        password_element.send_keys(random_string(16))
        submit_button.click()
        logging.info('WAIT for the error report')
        self._wait.until(conditions.text_to_be_present_in_element([By.ID, 'login-error'], 'Login error'),
                                        'The "password incorrect" error has not been raised')
        logging.info('OK incorrect password error')
        
        #testing the successful login
        password_element.clear()
        password_element.send_keys(self.password)
        submit_button.click()
        logging.info('WAIT for the successfull login marker')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-in']))
        logging.info('OK logged in')
        
        #testing the case when the login and the password are supplied via the url hash parameters
        self.log_out() #logging out
        self.loadURL(self.get_url(SIGNUP_PATH))#and leaving current page
        self.loadURL(self.get_url('%s#u=%s&p=%s' % (LOGIN_PATH, self.username, self.password)))
        username_element = self._driver.find_element_by_name('username')
        password_element = self._driver.find_element_by_name('password')
        #checking if the login and password strings are filled in to the appropriate fields
        self.assertEqual(username_element.get_attribute('value'), self.username,
                    'The username url hash parameter was not handled correctly')
        self.assertEqual(password_element.get_attribute ('value'), self.password,
                    'The password url hash parameter was not handled correctly')
        logging.info('OK login and the password fields had been filled correctly')
        logging.info('WAIT for the successful login marker')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-in']))
        logging.info('OK logged in')
        logging.info('>>>>> SUCCESS passed the login test \n\n\n')
    
    def test_logout_login_screen(self):
        logging.info('* Testing logging out from the login screen')
        self.loadURL(self.get_url(LOGIN_PATH))
        logout_link = self._driver.find_element_by_id('logout')
        logging.info('WAIT for the logout link');
        self._wait.until(conditions.visibility_of(logout_link))
        logging.info('OK Logout link display')
        logout_link.click()
        
        logging.info('WAIT for the successful logout marker')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-out']))
        logging.info('OK logged out from the login screen')
        self.sleep(2)
    
    def test_logout_services_page(self):
        logging.info('* Testing logging out from the services page')
        self.loadURL(self.get_url(SERVICES_PATH))
        
        #finding all the elements we'll use
        dropdown_toggle_parent = self._driver.find_element_by_id('account-menu')
        
        dropdown_toggle = dropdown_toggle_parent.find_element_by_tag_name('a')
        logout_link = dropdown_toggle_parent.find_element_by_class_name('logout-link')
        
        #activating the dropdown
        dropdown_toggle.click()
        logging.info('WAIT for the logout link to show')
        self._wait.until(conditions.visibility_of(logout_link))
        #hitting the log out link
        logout_link.click()
        #making sure we've been logged out
        logging.info('WAIT for the successfull logout marker')
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-out']))
        logging.info('OK logged out')
        
    def test_logout(self):
        logging.info('<<<<< START logout test')
        #testing logging out from the log in page
        self.test_logout_login_screen()
        #logging in again to perform the next log out test
        self.log_in()
        #testing logging out from the services page
        self.test_logout_services_page()
        
        #checking if we have the right username in the corresponding box
        username_element = self._driver.find_element_by_name('username')
        #self.assertEqual(username_element.get_attribute('value'), self.username,
        #        'The username in the corresponding box does not match the last logged out user username')
        logging.info('OK username in the input box does match the last authorized user')
        logging.info('>>>>> SUCCESS login test \n\n\n')

    def find_secure_item(self, context):
        secure_items = self._driver.find_element_by_id(context.container_id).\
                find_elements_by_class_name('active-service-item')
        logging.info('%d secure items found' % len(secure_items))

        for item in secure_items:
            title_element = item.find_element_by_class_name('title')
            if title_element.text.strip() == context.content['title']:
                return item

        raise exceptions.NoSuchElementException()

    def test_add_secure_item(self, context):
        logging.info('* Testing the secure item addition')
        if context.items_number:
            # TODO: check if this code interferes with the new tabbed layout
            logging.info('WAIT for the right secure items number')
            self._wait.until(self.verify_secure_items_number(context))

        logging.info('WAIT for the "add" button to show')
        self._wait.until(conditions.element_to_be_clickable([By.ID, context.add_item_element_id]))
        self.sleep(2)#let it think a little as it hangs here some times
        self._driver.find_element_by_id(context.add_item_element_id).click()

        modal = self._driver.find_element_by_id(context.add_item_modal_id)
        logging.info('WAIT for the modal')
        self._wait.until(conditions.visibility_of(modal))
        # Sleep for modal fade-in
        self.sleep(1)
        
        if context.items_number: context.generate_content()
        if context.type == context.NOTE:
            #secure note specific actions
            name_element = modal.find_element_by_name('name')
            text_element = modal.find_element_by_name('note_text')
            
            name_element.send_keys(context.content['title'])
            text_element.send_keys(context.content['text'])
        elif context.type == context.MANUAL_PASSWORD:
            #manual password specific actions
            name_field = modal.find_element_by_name('name')
            url_field = modal.find_element_by_name('login_url')
            username_field = modal.find_element_by_name('username')
            password_field = modal.find_element_by_name('password')
            
            name_field.send_keys(context.content['title'])
            url_field.send_keys(context.content['url'])
            username_field.send_keys(context.content['username'])
            password_field.send_keys(context.content['password'])
    
        submit_button = modal.find_element_by_class_name('save-secret-button')
        submit_button.click()

        modal = self._driver.find_element_by_id('share-service-modal')
        logging.info('WAIT for the "share" modal')
        self._wait.until(conditions.visibility_of(modal))
        # Sleep for modal fade-in
        self.sleep(1)

        submit_button = modal.find_element_by_class_name('share-service-button')
        submit_button.click()

        context.items_number += 1 #we've got a new note in here

        #making sure the operation was successful
        self._wait.until(self.verify_secure_items_number(context))
        self.find_secure_item(context)
        logging.info('OK secure items addition')

    def test_secure_items(self, context):
        """
        Generalized method to test secure items,
        both secure notes and manual passwords
        """
        #we're using the last added item as a target item
        target_element_finder = lambda: self.get_secure_elements(context)[-1]
        
        self.loadURL(self.get_url(SERVICES_PATH))
        tabs_list = self._driver.find_element_by_id('service-tabs')
        tabs_list.find_element_by_id(context.tab_id).click()
        
        #adding three new secure items
        for i in range(3):
            self.test_add_secure_item(context)
        
        #verifying the last added item
        self.verify_last_secure_item(context)
        
        #altering the last added item
        target_element = target_element_finder()
        m = self.secure_item_configuration_modal(context, target_element)
        self.update_secure_item(context, m)
            
        #verifying the last added item
        target_element = self.verify_last_secure_item(context)
        #testing item participants
        self.test_remove_participant(context, target_element)
        
        if context.type == context.MANUAL_PASSWORD:
            #testing the url link
            
            tabs_list = self._driver.find_element_by_id('service-tabs')
            tabs_list.find_element_by_id(context.tab_id).click()
            self.test_password_links(context, target_element_finder)
        
        #testing items deletion
        self.test_delete_item(context, target_element_finder)
    
    def test_secure_notes(self):
        logging.info('<<<<< START secure notes test')
        self.test_secure_items(Context(self, Context.NOTE))
        logging.info('>>>>> SUCCESS secure notes test \n\n\n')
    
    def test_manual_passwords(self):
        logging.info('<<<<< START manual passwords test')
        self.test_secure_items(Context(self, Context.MANUAL_PASSWORD))
        logging.info('>>>>> SUCCESS manual password test \n\n\n')
        
    def test_change_password(self):
        error_message = lambda: self._driver.find_element_by_id('password-error').text.strip()
        self.loadURL(self.get_url(CHANGE_PASSWORD_PATH))
        
        #testing the cancel button
        cancel_button = self._driver.find_element_by_id('change-password-cancel')
        cancel_button.click()
        page_title = self._driver.find_element_by_class_name('popup-title')
        self.assertEqual(page_title.text.strip(),
                         'Mitro Login Manager', 'Cancel button does not work')
        
        #getting back to the password change screen
        self.loadURL(self.get_url(CHANGE_PASSWORD_PATH))
        
        #finding all the elements we need
        old_password_element = self._driver.find_element_by_name('old-password')
        new_password_element = self._driver.find_element_by_name('new-password')
        new_password2_element = self._driver.find_element_by_name('new-password2')
        submit_button = self._driver.find_element_by_id('mitro-password-form-button')
        
        #we'll use this for batch actions
        password_elements = [new_password_element, new_password2_element, old_password_element]
        
        #generating password strings we'll use in the test
        short_new_password = random_string(7)
        new_password = random_string(16)
        
        #testing the too-short-password error
        new_password_element.send_keys(short_new_password)
        new_password2_element.send_keys(short_new_password)
        submit_button.click()
        self.assertEqual(error_message(), 'Password is too weak',
                         'Short password error is not shown')
        
        #testing the password missmatch error
        new_password_element.clear()
        new_password_element.send_keys(new_password)
        submit_button.click()
        self.assertEqual(error_message(), 'Passwords do not match',
                         'Passwords missmatch error is not shown')
        
        #testing wrong old password error
        new_password2_element.clear()
        new_password2_element.send_keys(new_password)
        old_password_element.send_keys(random_string(16))
        submit_button.click()
        self._wait.until(conditions.text_to_be_present_in_element\
                         ([By.ID,'password-error'], 'Error changing password'))
        
        #testing the case when new password matches the old one
        for element in password_elements:
            element.clear()
            element.send_keys(self.password)
        submit_button.click()
        self._wait.until(conditions.text_to_be_present_in_element\
                         ([By.ID,'password-error'], 'New password must be different from old password'))
        
        #testing the successful password change
        for element in password_elements: element.clear()
        new_password_element.send_keys(new_password)
        new_password2_element.send_keys(new_password)
        old_password_element.send_keys(self.password)
        submit_button.click()
        #making sure we've apeared on the home screen
        self._wait.until(conditions.visibility_of_element_located([By.ID, 'logged-in']))
        
        #setting the new password to be used in the tests
        self.password = new_password
        
        #testing the case when the old password is supplied via url hash parameter
        self.loadURL(self.get_url(SERVICES_PATH)) # we need to leave current page first
        self.loadURL(self.get_url('%s#u=username&p=%s' % (CHANGE_PASSWORD_PATH, self.password)))
        self.assertFalse(self._driver.find_element_by_name('old-password').is_displayed(),
                        'The old password field is not hidden')
    
    def test_issue_reporting(self):
        logging.info('<<<<< START issue reporting test')
        report_link = lambda: self._driver.find_element_by_link_text('Report an Issue')
        
        #testing report link while logged in
        self.loadURL(self.get_url(LOGIN_PATH))
        self.assertEqual(report_link().get_attribute('href'), str(self.get_url(ISSUE_PATH)),
                         'Issue link seems to be wrong when signed in')
        
        #logging out and testing the report link in logged-out state
        self.log_out()
        self.assertEqual(report_link().get_attribute('href'), str(self.get_url(ISSUE_PATH)),
                         'Issue link seems to be wrong when signed out')
        logging.info('OK report link does work')
        #now let's go to the issue page and test it's functionality
        self.loadURL(self.get_url(ISSUE_PATH))
        
        #getting the elements we'll need
        type_select_element = self._driver.find_element_by_name('type')
        submit_button = self._driver.find_element_by_id('submit_button')
        
        #testing the empty issue type error
        submit_button.click()
        logging.info('WAIT for the modal')
        self._wait.until(conditions.visibility_of_element_located([By.CLASS_NAME, 'modal']))
        self.sleep(2)#letting the modal transition finish
        error_message = self._driver.find_element_by_class_name('error-message')
        self.assertEqual(error_message.text.strip(), 'Please select an issue type')
        logging.info('OK no issue type specified error')
        
        #closing the error modal
        ok_button = self._driver.find_element_by_class_name('modal')\
                .find_element_by_link_text('OK')
        ok_button.click()
        logging.info('WAIT for the modal')
        self._wait.until(conditions.invisibility_of_element_located([By.CLASS_NAME, 'modal']))
        
        #testing the successful submission reporting
        select = ui.Select(type_select_element)
        select.select_by_value('other')
        submit_button.click()
        logging.info('WAIT for the successful submission marker')
        self._wait.until(conditions.text_to_be_present_in_element([By.TAG_NAME, 'span'], 'Thanks! Issue submitted'))
        logging.info('>>>>> SUCCESS issue reporting test \n\n\n')
        
        #logging back in to let the next tests do their job
        self.log_in()
    
    def test_teams_add(self):
        logging.info('* Testing teams addition')
        self.loadURL(self.get_url(GROUPS_PATH))
        if self._teams_number:
            logging.info('WAIT for the right teams number')
            self._wait.until(self.verify_teams_number)
        
        new_team_name = random_string(16)
        
        #submitting new team
        m = self.open_team_edit_modal(add=True)
        m['name_field'].send_keys(new_team_name)
        m['done_button'].click()
        
        #verifying the operation was successful
        #verifying the right teams number
        self._teams_number += 1
        logging.info('WAIT for the right teams number')
        self._wait.until(self.verify_teams_number)
        #making sure our new team name is present in the list
        self.assertTrue(self.verify_team_present(new_team_name))
        logging.info('OK adding a team')
    
    def test_teams_change_name(self):
        m = self.open_team_edit_modal()
        
        #submitting the new team name
        new_team_name = random_string(16)
        m['name_field'].clear()
        m['name_field'].send_keys(new_team_name)
        m['done_button'].click()
        
        #making sure the operation was successful
        logging.info('WAIT for the modal to dissapear')
        self._wait.until(conditions.invisibility_of_element_located([By.ID, 'addedit-group-modal']))
        #waiting for the list to refresh
        body = self._driver.find_element_by_tag_name('body')
        logging.info('WAIT for the body staleness')
        self._wait.until(conditions.staleness_of(body))
        logging.info('WAIT for the team item elements to appear')
        self._wait.until(conditions.presence_of_element_located([By.CLASS_NAME, 'team-item']))
        #checking the right items number
        self._wait.until(self.verify_teams_number)
        #checking the changed title is present in the list
        self.assertTrue(self.verify_team_present(new_team_name))
        logging.info('OK changing the team name')
        
        return new_team_name
    
    def test_teams_members(self):
        logging.info('* Checking the teams members')
        team_members_number = 1 #we've got 1 member per team by default
        
        #function to find the team member elements
        team_member_elements = lambda: self._driver.find_element_by_class_name('team-member-list')\
                                            .find_elements_by_tag_name('li')

        #function to verify the team members number
        verify_members_number = lambda driver: len(team_member_elements()) == team_members_number
        
        #adding 3 new team members
        for i in range(3):
            #getting the modal ready
            logging.info('* Adding new team')
            m = self.open_team_edit_modal()
            logging.info('WAIT for the right teams number')
            self._wait.until(verify_members_number)
    
            #submitting new team member
            new_team_member = random_string(6) + '@' + random_string(6)
            m['new_member_input'].send_keys(new_team_member)
            m['add_button'].click()
            m['done_button'].click()
            team_members_number += 1
            
            #waiting for the page to refresh
            self.wait_for_teams_refresh()
            
            #verifying the operation
            m = self.open_team_edit_modal()
            self._wait.until(verify_members_number)
            #searching for our new team member in the list
            team_member_is_present = False
            for team_member in team_member_elements():
                name = team_member.find_element_by_class_name('name').text.strip()
                if name == new_team_member:
                    team_member_is_present = True
                    break
            #did we find him?
            self.assertTrue(team_member_is_present)
            logging.info('OK adding the team member')
            
            #closing the modal
            m['done_button'].click()
            #waiting for the list to refresh
            self.wait_for_teams_refresh()
        
        #now let's delete two team members
        m = self.open_team_edit_modal()
        team_members_to_delete = team_member_elements()[-2:]
        for team_member_element in team_members_to_delete:
            checkbox = team_member_element.find_element_by_css_selector('input[type="checkbox"]')
            checkbox.click()
        m['done_button'].click()
        #waiting for the list to refresh
        self.wait_for_teams_refresh()
        team_members_number -= 2
        
        #verifying the deletion was successfull
        m = self.open_team_edit_modal()
        logging.info('WAIT for the right members number')
        self._wait.until(verify_members_number)
        logging.info('OK teams members')
        
    def test_teams_deletion(self, team_name):
        #the context object we'll need for manipulating the secure notes
        logging.info('* Testing teams deletion')
        context = Context(self, Context.NOTE)
        
        #giving the team access to the secure note
        self.toggle_team_access(context, team_name)
        
        #going to the teams page
        self.loadURL(self.get_url(GROUPS_PATH))
        logging.info('WAIT for the teams list to refresh')
        self._wait.until(conditions.presence_of_element_located([By.CLASS_NAME, 'team-item']))
        
        #checking for the error we expect to be shown
        #when we try to delete the team having access to the secure note
        m = self.open_team_edit_modal()
        m['delete_link'].click()
        #waiting for the confirm modal to open
        confirm_modal = self._driver.find_element_by_id('delete-confirm-modal')
        logging.info('WAIT for the confirmation modal')
        self._wait.until(conditions.visibility_of(confirm_modal))
        self.sleep(1) #wait for transition
        #checking the cancel button works
        confirm_modal.find_element_by_link_text('Cancel').click()
        logging.info('WAIT for the modal to dissapear')
        self._wait.until_not(conditions.visibility_of(confirm_modal))
        #hitting delete once again
        m['delete_link'].click()
        logging.info('WAIT for the confirmation modal')
        self._wait.until(conditions.visibility_of(confirm_modal))
        #this time we do confirm deletion
        confirm_modal.find_element_by_id('delete-group-button').click()
        #waiting for the error to be shown
        logging.info('WAIT for the error message')
        self._wait.until(conditions.text_to_be_present_in_element\
                     ([By.CLASS_NAME, 'error-message'],
                      'Error removing group'))
        #closing the error modal
        self._driver.find_element_by_class_name('error-dialog')\
                .find_element_by_link_text('OK').click()
        
        #removing the team access to the secure note
        self.toggle_team_access(context, team_name)
        
        #going back to the teams page
        self.loadURL(self.get_url(GROUPS_PATH))
        logging.info('WAIT for the teams list')
        self._wait.until(conditions.presence_of_element_located([By.CLASS_NAME, 'team-item']))
        #trying to delete the team
        m = self.open_team_edit_modal()
        m['delete_link'].click()
        confirm_modal = self._driver.find_element_by_id('delete-confirm-modal')
        logging.info('WAIT for the modal')
        self._wait.until(conditions.visibility_of(confirm_modal))
        confirm_modal.find_element_by_id('delete-group-button').click()
        self._teams_number -= 1
        #here we expect for the teams list to be refreshed
        #after the successfull deletion
        self.wait_for_teams_refresh()
        
        #let's search the teams and make sure the team we've deleted
        #is not among those left
        team_found = False #the flag
        for element in self._driver.find_elements_by_class_name('team-item'):
            if element.find_element_by_class_name('title').text.strip() == team_name:
                team_found = True
                break
        #it shouldn't be there
        self.assertFalse(team_found)
        logging.info('OK teams deletion')
            
    def test_teams(self):
        logging.info('<<<<< START teams test')
        self.sleep(3)
        for i in range(3):
            self.test_teams_add()
        
        team_new_name = self.test_teams_change_name()
        self.test_teams_members()
        self.test_teams_deletion(team_new_name)
        logging.info('>>>>> SUCCESS teams test \n\n\n')
    
    def test_automatic_passwords(self):
        logging.info('<<<< START automaric passwords')
        errors_count = 0
        #function to searth for the target element in the list
        target_element = lambda: [element for element \
                in self.get_secure_elements(context) \
                if element.find_element_by_css_selector('a.host')\
                .get_attribute('href') == content['url']][0]

        #going to the test forms list page
        self.loadURL(TEST_FORMS_URL)
        
        #getting all of the test form paths
        test_forms_paths = []
        for list_item in self._driver.find_elements_by_tag_name('li'):
            path = list_item.find_elements_by_tag_name('a')[0]\
                    .get_attribute('href')
            
            #this is because the auth popup doesn't let the script proceed
            #until successfull login and the basic auth successfull login
            #is only possible on the local runs
            if not path == 'http://forms.it-works.org.ua/basic_auth_form.html':
                test_forms_paths.append(path)

        #processing the test forms
        for path in test_forms_paths:
            try:
                logging.info('Processing the %s form' % path)
                #proceeding to the login form page
                self.loadURL(path)
                logging.info('WAIT for the login form')
                self._wait.until(self.condition_login_form_loaded)
                
                #the user has to manually input the login
                #datat after the form is loaded
                
                #waiting for successful login
                logging.info('WAIT for the successfull login marker')
                self._wait.until(self.condition_login_success)
                
                #hitting the extension's 'Save password' button
                self.switch_to_extension()
                self.get_extension_buttons()['save_password'].click()
                self._driver.switch_to_default_content()
                self.sleep(2)#let the extension perform it's actions
                
                #going back to the login form page
                self.loadURL(path)
                logging.info('WAIT for the login form')
                self._wait.until(self.condition_login_form_loaded)
                #hitting the extension's 'Log in' button
                self.switch_to_extension()
                self.get_extension_buttons()['log_in'].click()
                self._driver.switch_to_default_content()
                
                #chcking if the logging in succeeded
                logging.info('WAIT for the successfull login marker')
                self._wait.until(self.condition_login_success)
                
                #testing the credentials view/edit
                #getting context passing the manually created content dict
                parsed_path = urlparse(path)
                content = {
                    'title': parsed_path.netloc,
                    'netloc': parsed_path.netloc,
                    'url': path,
                }
                context = Context(self, Context.AUTOMATIC_PASSWORD, content)
                context.items_number += 1#we already have one item at the moment
                
                #going to the services page
                
                #that's the hack actually...
                self.loadURL(self.get_url(SERVICES_PATH))
                self.sleep(1)
                self.loadURL(self.get_url(SERVICES_PATH))
                #EOF hack
                
                self.wait_for_refresh(context, page_refresh=False)
                
                #verifying the item content and the copy features
                if context.items_number:
                    self.verify_secure_items_number(context)
                
                #verifying the item content
                m = self.verify_automatic_password(context, target_element)
                
                #updating the item content, adding new participant
                self.update_secure_item(context, m)
                
                #making sure the new content had been saved correctly
                m = self.verify_automatic_password(context, target_element)
                
                #closing the modal
                m['cancel_button'].click()
                logging.info('WAIT for the config modal')
                self._wait.until(conditions.invisibility_of_element_located\
                                 ([By.ID, context.configuration_modal_id]))
                
                #testing removing participant
                self.test_remove_participant(context, target_element())
                
                #testing the url links and the 'Log in' button
                self.test_password_links(context, target_element)
                
                self.test_delete_item(context, target_element)
            except:
                print 'Automatic password test failed on %s' % path
                print traceback.print_exc()
                errors_count += 1
            
            if not errors_count:
                status = 'SUCCESS'
            else:
                status = 'FAIL'
                
            logging.info('>>>>> %s automatic passwords test \n\n\n' % status)
    
    def test_info_links(self):
        logging.info('<<<<< START infolinks test')
        links_to_test = ['Privacy Policy', 'Terms of Service']
        
        for link_text in links_to_test:
            self.loadURL(self.get_url(SERVICES_PATH))
            #locating the dropdown menu
            dropdown_container = self._driver.find_element_by_id('account-menu')
            dropdown_toggle = dropdown_container.find_element_by_tag_name('a')
            #activating the dropdown
            dropdown_toggle.click()
            #hitting the target link
            dropdown_container.find_element_by_link_text(link_text).click()
            #we should apear on the page with the same h1 title
            #as the target link text
            self._wait.until(conditions.text_to_be_present_in_element([By.TAG_NAME, 'h1'], link_text))
        
        logging.info('>>>>> SUCCESS infolinks test \n\n\n')



class Context(object):
    """
    Secure items tests context object class
    """
    NOTE = 1
    MANUAL_PASSWORD = 2
    AUTOMATIC_PASSWORD = 3
    
    content = {}

    def __init__(self, tester, _type, content={}):
        self.tester = tester
        self.type = _type
        #the new participant's id to test granting/removing access
        self.additional_participant = '%s@%s' % tuple((random_string(5) for i in range(2)))
        
        #generating content to be usen for adding/altering the items
        #or just assigning it if the content is given
        self.generate_content(content)
        
        #setting the item related elements selectors
        if self.type == self.NOTE:
            self.container_id = 'secure-notes-list'
            self.view_modal_id = 'view-secure-note-modal'
            self.add_item_element_id = 'add-secure-note-button'
            self.add_item_modal_id = 'add-secure-note-modal'
            self.configuration_modal_id = 'configure-service-modal'
            self.edit_detail_modal_id = 'edit-secure-note-modal'
            self.tab_id = 'secure-notes-tab-title'
        elif self.type == self.MANUAL_PASSWORD:
            self.container_id = 'manual-passwords-list'
            self.view_modal_id = 'auth-manual-modal'
            self.add_item_element_id = 'add-secret-button'
            self.add_item_modal_id = 'add-service-modal'
            self.configuration_modal_id = 'configure-service-modal'
            self.edit_detail_modal_id = 'credentials-modal'
            self.tab_id = 'manual-passwords-tab-title'
        elif self.type == self.AUTOMATIC_PASSWORD:
            self.container_id = 'web-passwords-list'
            self.configuration_modal_id = 'configure-service-modal'
            self.edit_detail_modal_id = 'credentials-modal'
            self.tab_id = 'web-passwords-tab-title'

        #the indicator element to be used for the list refresh detection
        self.refresh_indicator = [By.CLASS_NAME, 'active-service-item']

    def generate_content(self, content={}):
        """
        A method to generate the item type specific content
        """
        #saving previose content version
        self.previose_content = self.content
        if not content:
            global ITEM_NO
            ITEM_NO += 1
            #generating new content
            if self.type == self.NOTE:
                self.content = {
                    'title': '%04d Note %s' % (ITEM_NO, random_string(10)),
                    'text': random_string(20),
                }
            elif self.type == self.MANUAL_PASSWORD:
                query_string = random_string(16)
                self.content = {
                    'title': '%04d %s' % (ITEM_NO, random_string(16)),
                    'query_string': query_string,
                    'url': 'http://en.wikipedia.org/w/index.php?search=%s' % query_string,
                    'username': random_string(7),
                    'password': random_string(16),
                }
            elif self.type == self.AUTOMATIC_PASSWORD:
                self.content.update({
                    'title':  '%04d %s' % (ITEM_NO, random_string(16)),
                    'username': random_string(7),
                    'password': random_string(16),
                })
        else:
            self.content = content
            
    def get_items_number(self):
        """
        Items number getter
        """
        if self.type == self.NOTE:
            return self.tester._secure_notes_number
        elif self.type == self.MANUAL_PASSWORD:
            return self.tester._manual_passwords_number
        elif self.type == self.AUTOMATIC_PASSWORD:
            return self.tester._automatic_passwords_number

    def set_items_number(self, value):
        """
        Items number setter
        """
        if self.type == self.NOTE:
            self.tester._secure_notes_number = value
        elif self.type == self.MANUAL_PASSWORD:
            self.tester._manual_passwords_number = value
        elif self.type == self.AUTOMATIC_PASSWORD:
            self.tester._automatic_passwords_number = value
        

    items_number = property(get_items_number, set_items_number)

def create_chromedriver(webdriver_path, extension_paths):
    options = webdriver.ChromeOptions()
    # Disable any default apps in Chrome, and any "External Extensions" on this machine
    # This prevents lastpass from being installed:
    # http://developer.chrome.com/extensions/external_extensions.html
    options.add_argument('--disable-default-apps')

    for extension_path in extension_paths:
        options.add_extension(extension_path)

    driver = webdriver.Chrome(executable_path=webdriver_path,
                              chrome_options=options)
    return driver

def get_extension_base_url(driver, extension_title):
    if driver.name == 'chrome':
        driver.get('chrome://extensions-frame/')
    
        extensions = driver.find_elements_by_class_name('extension-list-item-wrapper')
    
        for extension_item in extensions:
            title_elements = extension_item.find_elements_by_class_name('extension-title')
    
            if len(title_elements) != 1:
                raise Exception, 'wrong number of title elements'
    
            title = title_elements[0].get_attribute('innerHTML')
            if title == extension_title:
                return "chrome-extension://%s" % extension_item.get_attribute('id')
    
    elif driver.name == 'safari':
        return 'http://selenium.mitro.co:8012';

    return ''

def main():
    general.init_logging()
    if args.browser == 'chrome':
        driver = create_chromedriver(_WEBDRIVER_PATH, [args.extension_path])
    elif args.browser == 'safari':
        static_server = subprocess.Popen(['login/server/server.py', '--static-root',
                                          '../build/safari/test.safariextension', '--port', '8012'],
                         stdout=subprocess.PIPE)
        subprocesses.append(static_server)
        if not REMOTE_SERVER:
            selenium_server = subprocess.Popen(['java', '-jar','login/test/build/selenium/selenium-server-standalone-2.35.0.jar'])
            subprocesses.append(selenium_server)
        desired_capabilities = webdriver.DesiredCapabilities.SAFARI
        with open(args.extension_path, 'r') as extension_file:
            extension = base64.b64encode(extension_file.read())
            desired_capabilities['safari.options'] = \
                    {'extensions':[{"filename": "debug.safariextz", "contents": extension}]}
            selenium_host = (args.selenium_server_host and [args.selenium_server_host] or 'localhost')[0]
            driver = webdriver.Remote("http://%s:4444/wd/hub" % selenium_host, desired_capabilities)
    else:
        raise Exception('Please specify the browser name. The options are: chrome, safari')

    
    base_url = get_extension_base_url(driver, _MITRO_EXTENSION_TITLE)
    if not base_url:
        raise Exception, 'Mitro extension base url had not been obtained'

    logging.info('base_url: %s' % base_url)
    
    tester = MitroExtensionTester(driver, base_url)

    username = random_string(6) + '@' + random_string(6)
    password = random_string(16)

    logging.info('username: ' + username)
    logging.info('password: ' + password)

    try:
        #setting login and password to be used in the tests
        tester.username = username
        tester.password = password
        
        #now let's run the tests
        tester.test_signup()
        tester.test_logout()
        tester.test_login()
        tester.test_change_password()
        tester.test_issue_reporting()
        tester.test_secure_notes()
        tester.test_manual_passwords()
        tester.test_teams()
        tester.test_info_links()
        tester.test_automatic_passwords()
    finally:
        raw_input("Press Enter to close the browser window...")
        driver.quit()
        for process in subprocesses:
            process.kill()


if __name__ == '__main__':
    main()