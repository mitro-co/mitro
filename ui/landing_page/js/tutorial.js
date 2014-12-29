
var tutorialStages = [
		['Accessing Mitro', 'img/tutorial-1.png', 'Click this button to access Mitro from any page.'],
		['Saving Web Passwords', 'img/tutorial-2.png',
		 'Log in to any web site and Mitro will ask you if you would like to save your username and password.'],
		['Using Mitro To Login', 'img/tutorial-3.png', 'Go to a site for which Mitro has a saved password, and login with one click!'],
		['Sharing Your Secrets', 'img/tutorial-4.png', 'To share your secrets, select "Secrets and Services" on the popup menu, click "configure access" on the appropriate secret, and add collaborators.'],
		['Managing Teams', 'img/tutorial-5.png', 'To setup a team to share secrets with, press "Manage Teams" in the Secrets and Services page. Then, click "Add New Team," add a Team Name and Team Members. You are then able to share secrets with this team.'],
		['Secure Notes', 'img/tutorial-6.png', 'To save a secure note, press "Add Secure Note," and enter your information.'],
		['Two Factor Authentication', 'img/tutorial-7.png', 'Two Factor Authentication adds an extra layer of security to your Mitro account. Learn about it and enable it by pressing "Two Factor Auth Preferences" in the popup menu.'],
		['Getting Started', 'img/tutorial-8.png', 'You should have received an authentication email after signing up for Mitro. To get started, verify your account by pressing the button in the email.']

];

$(function() {
  var currentStage = -1; // pull this from the hash.
  var nextStage = function() {
    ++currentStage;
    if (currentStage >= tutorialStages.length) {
      window.location = '/extension_services.html';
      return;
    }
    $('#tutorial-title').text(tutorialStages[currentStage][0]);
    $('#tutorial-picture').attr('src', tutorialStages[currentStage][1]);
    $('#tutorial-caption').text(tutorialStages[currentStage][2]);

    if (currentStage + 1 === tutorialStages.length) {
      $('#link').html("Click here to begin using Mitro");
    }
  };
  $('#link').click(function() {
    nextStage();
  });
  nextStage();
});