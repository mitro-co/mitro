$(document).ready(function () {
  Stripe.setPublishableKey('pk_live_4fmxxXbDuhIdI4G9uW4gA6Cw');

  var $paymentForm = $('#payment-form');

  var $planName = $paymentForm.find('.plan-name');
  var $planUnitCost = $paymentForm.find('.plan-unit-cost');
  var $orgName = $paymentForm.find('.org-name');
  var $orgUsers = $paymentForm.find('.org-users');
  var $totalCost = $paymentForm.find('.total-cost');

  var $cardNumberInput = $paymentForm.find('#cc-number');
  var $cardCVCInput = $paymentForm.find('#cc-cvc');
  var $monthInput = $paymentForm.find('#cc-month');
  var $yearInput = $paymentForm.find('#cc-year');

  var $cardNumberError = $paymentForm.find('#cc-number-error');
  var $cardCVCError = $paymentForm.find('#cc-cvc-error');
  var $expirationError = $paymentForm.find('#cc-expiration-error');

  var $submitButton = $paymentForm.find('.button');

  $cardNumberInput.payment('formatCardNumber');
  $cardCVCInput.payment('formatCardCVC');
  $monthInput.payment('restrictNumeric');
  $yearInput.payment('restrictNumeric');

  var customerToken = window.location.hash.slice(1);
  $paymentForm.find('input[name="customer_token"]').val(customerToken);

  var renderSubscription = function (response) {
    var unitCost = response.planUnitCost / 100;
    var totalCost = unitCost * response.numUsers;

    $orgName.text(response.orgName);
    $orgUsers.text(response.numUsers);
    $planName.text(response.planName);
    $planUnitCost.text('$' + unitCost + ' per user, per month');
    $totalCost.text('$' + totalCost + ' per month');

    $submitButton.prop('disabled', false);
  };

  var onGetCustomerError = function (response) {
    console.log(response);
    alert('Invalid customer');
  };

  $.ajax({
      type: 'GET',
      url: '/mitro-core/GetCustomer',
      data: {'customer_token': customerToken},
      dataType: 'json',
      success: renderSubscription,
      error: onGetCustomerError
  });

  var stripeResponseHandler = function (status, response) {
    console.log(response); 

    if (response.error) {
      $cardNumberError.text(response.error.message);
      $submitButton.prop('disabled', false);
    } else {
      var token = response.id;
      $paymentForm.find('input[name="cc_token"]').val(token);
      $paymentForm.get(0).submit();
    }
  };

  $paymentForm.submit(function (e) {
    e.preventDefault();

    var $form = $(this); 

    var cardNumber = $cardNumberInput.val();
    var cardCVC = $cardCVCInput.val();
    var month = $monthInput.val();
    var year = $yearInput.val();

    var error = false;

    if (!Stripe.card.validateCardNumber(cardNumber)) {
      $cardNumberError.text('Invalid card number');
      error = true;
    } else {
      $cardNumberError.text('');
    }

    if (!Stripe.card.validateExpiry(month, year)) {
      $expirationError.text('Invalid expiry date');
      error = true;
    } else {
      $expirationError.text('');
    }

    if (!Stripe.card.validateCVC(cardCVC)) {
      $cardCVCError.text('Invalid CVC');
      error = true;
    } else {
      $cardCVCError.text('');
    }

    if (!error) {
      $submitButton.prop('disabled', true);
      Stripe.card.createToken($form, stripeResponseHandler);
    }
  });
});
