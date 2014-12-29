	$(window).load(function() {
		$('img').show();
	});
$(function(){
	$('#slideshow-slides').hide();



	$(document).foundation();

	$('#slideshow-slides').show();
	$('#slideshow-container .orbit-prev').hide();
	$('#slideshow-container .orbit-next').hide();
	$('#slideshow-container .orbit-timer').hide();

	$('.orbit-bullets>li').on({
		mouseenter: function () {
			// Set the active slide
			$(this).click();

			// Reset the active class for the captions
			$('.caption-content').each(function () {
				$(this).removeClass('active');
				$(this).css({'display': 'none'});
			});

			$('#caption-' + this.getAttribute('data-orbit-slide-number')).css({'display': 'block'});
		}
	});
});
