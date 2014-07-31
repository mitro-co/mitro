#!/usr/bin/env python
import sys
import random
import heapq

DISTRIBUTION = [
341,
444,
551,
656,
765,
906,
1130,
1588,
3313,
84399
]

EVENTS_PER_MS = 5754. / 431997259

# do a simulation

MAX_TIME = 60 * 60 * 24 * 1000

if __name__ == '__main__':
    scale_factor = int(sys.argv[1])
    exp_lambda = (EVENTS_PER_MS * scale_factor)
    num_pending_transactions = []

    # a new event shows up according to the random variable, and lasts for 
    # some time based on the distribution of percentiles

    current_time = 0
    count = 0
    pending_events = []
    while current_time < MAX_TIME:
        count += 1
        current_time += random.expovariate(exp_lambda)
        # new event!
        heapq.heappush(pending_events, current_time + random.choice(DISTRIBUTION))

        while pending_events and (pending_events[0] < current_time):
            heapq.heappop(pending_events)
        num_pending_transactions.append(len(pending_events))

    print 'total transactions: ', count
    print 'max pending at any time: ', max(num_pending_transactions)
    mean = sum(num_pending_transactions) / len(num_pending_transactions)
    print 'average pending: ', mean
    print ('stddev pending: ',
        (sum([(x-mean)**2 for x in num_pending_transactions]) / len(num_pending_transactions))**.5
        )
