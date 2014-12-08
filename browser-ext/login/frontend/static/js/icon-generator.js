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

var generateIcon;
var generateTag;
var addTags;
var replaceBlankImages;
(function () {
    'use strict';

    // Generate a color based on the hash of a string.
    var bkCol = function(text) {
        var colourSet = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728', '#9467bd', '#8c564b', '#e377c2', '#7f7f7f', '#bcbd22', '#17becf'];
        // Decent hash function from crappy crypto
        var hash = function(s){
          return s.split("").reduce(function(a,b){a=((a<<5)-a)+b.charCodeAt(0);return a&a;},0);              
        };
        var hashedValue = text ? Math.abs(parseInt(hash(text),10)) : 0;
        return colourSet[hashedValue % colourSet.length];
    };

    /**
    @param {string} label
    @param {string=} color
    */
    generateIcon = function (label, color) {
        if (!color) {
           color = bkCol(label); 
        }   

        var $newDiv = $('<div>');
        if (label) {
            $newDiv.css({
                'background-color': color,
                'color': 'white',
                'text-align': 'center'
            });
            $newDiv.text(label[0].toUpperCase());
            $newDiv.attr('title', label);
            $newDiv.attr('data-icon-text', label);
        }

        return $newDiv;
    };

    /**
    @param {string} label
    @param {string=} color
    */
    generateTag = function (label, color) {
        if (!color) {
            color = bkCol(label);
        }

        var $newDiv = $('<div>');
        $newDiv.css({
            'background-color': color,
            'color': 'white'
        });
        $newDiv.text(label);
        $newDiv.addClass('org-tag');

        return $newDiv;
    };

    // Add tags to a list of elements.
    //
    // $elements: jQuery set of elements.
    // insertSelector: the insertion point of tags within each element.
    // getLabelsForElementFunc: a user supplied function that takes an
    //   element and returns a list of labels to be added to the element.
    addTags = function ($elements, insertSelector, getLabelsForElementFunc) {
        $elements.each(function () {
            var $element = $(this);
            var labels = getLabelsForElementFunc($element);
            var $prevElement = $element.find(insertSelector);

            for (var i = 0; i < labels.length; ++i) {
                var $labelTag = $(generateTag(labels[i]));
                $labelTag.insertAfter($prevElement);
                $prevElement = $labelTag;
            }
        });
    };

    /** @param {!jQuery} $icons */
    replaceBlankImages = function($icons) {
        for (var i = 0; i < $icons.length; ++i) {
            var $icon = $($icons[i]);
            if (!$icon.attr('src') || $icon.attr('src').indexOf('data:image') === 0) {
                // replace this image.
                var name = $icon.attr('data-icon-text');
                if (name) {
                    var $newDiv = generateIcon(/** @type {string} */ (name));
                    $newDiv.attr('class', $icon.attr('class'));
                    $icon.replaceWith($newDiv);
                }
            }
        }
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {generateIcon: generateIcon,
                          generateTag: generateTag,
                          addTags: addTags, 
                          replaceBlankImages: replaceBlankImages};
    }
})();
