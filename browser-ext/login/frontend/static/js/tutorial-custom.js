/*
 * *****************************************************************************
 * Copyright (c) 2012, 2013, 2014 Lectorius, Inc.
 * Authors:
 * Vijay Pandurangan (vijayp@mitro.co)
 * Evan Jones (ej@mitro.co)
 * Adam Hilss (ahilss@mitro.co)
 *
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *     You can contact the authors at inbound@mitro.co.
 * *****************************************************************************
 */

$(window).bind("load", function() {
    $('#tutorial').modal({
        show: true
    });
});

$(document).ready(function() {
    //toggle arrow on collapse
    $('.team-group button').click(function() {
        $(this).find('i').toggleClass('ico-arrow-down');
    });

    //tutorial navigation
    $('.btn-step').click(function() {
        var step = $('.tab-pane.active').attr('id');
        var id = parseInt(step.substring(3), 10);
        $('.tab-pane').removeClass('active');

        if ($(this).hasClass('next')) {
            id++;
            if (id === 2) {
                $('.prev').removeClass('hidden');
            }
            if (id === 5) {
                $(this).addClass('hidden');
                $('.done').removeClass('hidden');
            }
        } else {
            id--;
            if (id === 1) {
                $('.prev').addClass('hidden');
            }
            if (id === 4) {
                $('.next').removeClass('hidden');
                $('.done').addClass('hidden');
            }
        }

        $('#tab' + id).addClass('active');

        $('.steps li').removeClass('active');
        $('.steps li').eq(id-1).addClass('active');
    });
});
