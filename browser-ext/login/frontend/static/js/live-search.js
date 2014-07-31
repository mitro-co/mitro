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

/**
@param {!jQuery} $searchBox
@param {string} itemFilterSelector
@param {!jQuery=} $clearSearchButton
*/
var registerLiveSearch = function($searchBox, itemFilterSelector, $clearSearchButton) {
  var memberRank = function($member, strs) {
    var text = $member.find('.search-this-value').text().toLocaleLowerCase();
    var responses = [];
    for (var i = 0; i < strs.length; ++i) {
      if (text.indexOf(strs[i]) >= 0) {
        responses.push(1);
      } else {
        responses.push(0);
      }
    }
    return responses;
  };

  // boost to give to an exact string match for what you're searching for.
  var EXACT_MATCH_BOOST = 1<<10;
  var searchMembers = function (queryString) {
    var $serviceItems = $(itemFilterSelector);
    var toSearch = queryString.toLocaleLowerCase();

    // promote exact strong matches.
    var searchTokens = [toSearch].concat(toSearch.match(/\S+/g));
    var ranking = {};

    for (var i = 0; i < $serviceItems.length; ++i) {
      var $member = $($serviceItems[i]);
      var matchResponses = [];
      var numMatched = 0;
      if (toSearch) {
        matchResponses = memberRank($member, searchTokens);
        numMatched = EXACT_MATCH_BOOST * matchResponses[0];
        for (var j = 1; j < matchResponses.length; ++j) {
          if (matchResponses[j]) {
            numMatched += matchResponses[j];
          } else {
            // TODO: this means we only return matches where all keywords match
            // is that what we want? Removing this will have keywords ranked by 
            // scoring.
            numMatched = 0; 
            break;
          }
        }
      }
      ranking[$member.attr('id')] = numMatched;
      if (numMatched > 0 || !toSearch) {
        $member.attr('data-matches', true);
        $member.show();
      } else {
        $member.attr('data-matches', false);
        $member.hide();
      }
    }

    // sort items according to matches.
    // must call detach() to preserve any handlers once the items are re-added.

    // TODO: rank by most common.
    var $parent = $serviceItems.parent();
    $serviceItems.detach().sort(function(left, right) {
      var lRank = ranking[$(left).attr('id')];
      var rRank = ranking[$(right).attr('id')];
      if (lRank === rRank) {
        // if the rank is the same, sort alphabetically (ascending)
        return lowercaseCompare($(left).find('.search-this-value').text().trim().toLocaleLowerCase(), 
          $(right).find('.search-this-value').text().trim().toLocaleLowerCase());
      } else {
        // sort by rank (descending)
        return (rRank > lRank) ? 1 : -1;
      }
    });
    $parent.append($serviceItems);
  };
  $searchBox.keyup(function() {
    var queryString = $searchBox.val();

    if ($clearSearchButton) {
      // show/hide clear search button
      if (queryString) {
        $clearSearchButton.removeClass('hide');
      } else {
        $clearSearchButton.addClass('hide');
      }
    }

    searchMembers(queryString);
  });

  if ($clearSearchButton) {
    // clear search field input
    $clearSearchButton.click(function () {
      $clearSearchButton.addClass('hide');
      $searchBox.val('').focus();
      searchMembers('');
    });
  }
};
