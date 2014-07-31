New UI Prototypes
====================

These files comprise the new UI for the password management services pages.  It has an updated look and feel to match the browser extension popup and a more intuitive interface.

Navigation
----------
The sidebar navigation is updated with separate sections for personal secrets and organization secrets.  Organizations are also associated with a particular color, which is used throughout the design to distinguish secrets / teams that belong to a particular organization.

#### Personal
- Personal section includes secrets, teams, and audit logs
- Secrets include all of the user's secrets, including any secrets they are given access to through an organization.
- Teams include all of the user's teams, including organization teams
*Audit logs for personal accts are not currently supported but the new UI reflects the inclusion of the feature*

#### Organization User (Admin)
- If the user is an admin of an organization, they should have access to everything, including secrets, teams, members, audit logs, and the ability to sync with Google
- In the navigation area, the user's admin status is indicated by the ADMIN tag
*Note: One challenge is choosing what to display on the secrets page.  The admin should have access to all the secrets in the organization for management purposes, but may not actually be a "member" of a secret.  There should be another visual indicator so the admin can easily see which secrets they are members of.*

#### Organization User (Non-admin)
- If the user is not an admin of an organization, they will only see secrets and teams - secrets they have access to and teams they are members of.

#### Other Tools
- Quick access to import secrets (import-secrets.html)
- Feedback form
- Sign Out
- *Should include a link to settings - to change password an other account info*

Secrets
---------
index.html (personal view)
org-secrets.html (view of page when user does not have any secrets yet)
- Color circles next to secrets indicate organization the secret belongs to
- Buttons on right for actions - sign in, copy username, copy password, share, and edit details
- For notes, there are three actions, copy note, share, edit details
- If there are no secrets yet, user should see how-to tutorial slideshow to learn how to add / access secrets

secret-info.html (manage secret page)
- Manage secret page has form to edit the secret - depending on whether the secret is a note or a web password, different fields will be displayed
- Separate tab to add members / teams to the secret
- If the secret belongs to an organization, the org name should be included in secret acct page (secret-org-info.html)

add-personal-secret.html (add personal secret page)
- Choose whether to make a web password or a note - fields change depending on secret type

Teams
---------
personal-teams.html (view when there are no teams yet)
org-teams.html (org teams)
- icons to indicate number of secrets each team has access to, and number of members on each team

team-info.html (manage team page)
- Edit team information.  If the team is part of an organization, the organization name should appear in the account info area
- Members tab - add and remove members to / from the team
- Secrets tab - add and remove secrets to / from the team

add-team.html (add team page)
- If the team is being created for an organization, show org name

Members (for organizations only)
---------
org-members.html
- Only admins have access to this view
- Can set the user access levels for each member

user-info.html (User info page)
- Teams tab - see which teams the user is a member of
- Secrets tab - see which secrets the user has access to

add-member.html (Add new member)

Audit Logs
---------
org-logs.html