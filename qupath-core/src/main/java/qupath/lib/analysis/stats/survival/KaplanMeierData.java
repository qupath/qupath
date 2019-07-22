/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.analysis.stats.survival;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Structure used to store data related to generating Kaplan-Meier survival curves.
 * 
 * @author Pete Bankhead
 *
 */
public class KaplanMeierData {

	private List<KaplanMeierEvent> events = new ArrayList<>();
	private String name;
	private double [] statisticCached;
	private double [] timesAllCached;
	private double [] timesEventsCached;
	private int [] atRiskCached;
	
	/**
	 * Create a new KaplanMeierData object with the specified display name.
	 * @param name
	 */
	public KaplanMeierData(final String name) {
		this.name = name;
	}

	/**
	 * Create a new KaplanMeierData object with the specified display name and events.
	 * @param name
	 * @param events
	 */	
	public KaplanMeierData(final String name, final Collection<KaplanMeierEvent> events) {
		this(name);
		this.events.addAll(events);
		Collections.sort(this.events);
	}

	/**
	 * Add a collection of events.
	 * @param events
	 * @return
	 */
	public KaplanMeierData addEvents(final Collection<KaplanMeierEvent> events) {
		this.events.addAll(events);
		Collections.sort(this.events);
		return this;
	}

	/**
	 * Insert a new event.
	 * @param time the time of the event (units are not specified, but should be consistent for all events added)
	 * @param censored if true the event is censored, if false the event is observed.
	 * @return
	 */
	public KaplanMeierData addEvent(final double time, final boolean censored) {
		KaplanMeierEvent event = new KaplanMeierEvent(time, censored);
		int ind = Collections.binarySearch(events, event);
		if (ind < 0)
			ind = -ind-1;
		events.add(ind, event);
		// Earlier, lazier version of the code...
//		events.add(event);
//		Collections.sort(events);
		resetCached();
		return this;
	}

	/**
	 * Get the name of this data, generally used for display.
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns true if there are no events included.
	 * @return
	 */
	public boolean isEmpty() {
		return this.events.isEmpty();
	}

	private void resetCached() {
		this.statisticCached = null;
		this.timesAllCached = null;
		this.atRiskCached = null;
	}


	private void compute() {
		// Get the unique times
		int n0 = events.size();
		timesAllCached = new double[n0 + 1];
		timesEventsCached = new double[n0 + 1];
		int ind = 0;
		double lastTimeAll = 0;
		int indEvents = 0;
		double lastTimeEvents = 0;
		for (KaplanMeierEvent event : events) {
			double time = event.getTimeToEvent();
			// Handle everything
			if (time > lastTimeAll) {
				ind++;
				timesAllCached[ind] = time;
				lastTimeAll = time;
			}
			// Handle uncensored events
			if (!event.isCensored() && time > lastTimeEvents) {
				indEvents++;
				timesEventsCached[indEvents] = time;
				lastTimeEvents = time;
			}
		}
		// Trim the arrays, if necessary - we must have multiple events at the same time
		if (ind < timesAllCached.length)
			timesAllCached = Arrays.copyOf(timesAllCached, ind+1);
		// Trim the arrays, if necessary - we must have multiple events at the same time
		if (indEvents+1 < timesEventsCached.length)
			timesEventsCached = Arrays.copyOf(timesEventsCached, indEvents+1);

		// Compute statistic
		// (This is done in a particularly unoptimized way...)
		statisticCached = new double[timesAllCached.length];
		atRiskCached = new int[timesAllCached.length];
		double lastProduct = 1;
		for (int i = 0; i < timesAllCached.length; i++) {
			double t = timesAllCached[i];
			// Update at risk
			int ni = getAtRisk(t);
			atRiskCached[i] = ni;
			int di = getEventsAtTime(t);
			// Update the statistic
			lastProduct = lastProduct * ((double)(ni - di) / ni);
			statisticCached[i] = lastProduct;
		}
	}

	/**
	 * Get an unmodifiable list of all events.
	 * @return
	 */
	public List<KaplanMeierEvent> getEvents() {
		return Collections.unmodifiableList(events);
	}

	private void ensureComputed() {
		if (timesAllCached == null)
			compute();
	}

	/**
	 * Retrieve a sorted array containing all times where 'something happened' (observed or censored).
	 * @return
	 * @see #getStatistic()
	 */
	public double[] getAllTimes() {
		ensureComputed();
		return Arrays.copyOf(timesAllCached, timesAllCached.length);
	}

//	public double[] getEventTimes() {
//		ensureComputed();
//		return Arrays.copyOf(timesEventsCached, timesEventsCached.length);
//	}
//
//	public int[] getAtRiskNumber() {
//		ensureComputed();
//		return Arrays.copyOf(atRiskCached, atRiskCached.length);
//	}

	/**
	 * Retrieve a sorted array containing the value corresponding to a time from {@link #getAllTimes()}.
	 * @return
	 * @see #getAllTimes()
	 */
	public double[] getStatistic() {
		ensureComputed();
		return Arrays.copyOf(statisticCached, statisticCached.length);
	}

	/**
	 * Get the time of the last event, or -1 if there are no events.
	 * @return
	 */
	public double getMaxTime() {
		if (events.isEmpty())
			return -1;
		return events.get(events.size()-1).getTimeToEvent();
	}

	//		private int getLastIndexForTime(final double t) {
	//			ensureComputed();
	//			int i = timesAllCached.length-1;
	//			while (i > 0) {
	//				// TODO: Check if > or >=
	//				if (timesAllCached[i] >= t)
	//					break;
	//				else
	//					i--;
	//			}
	//			return i;
	//		}

	/**
	 * Get the number at risk at a specified time. This includes events that occur precisely at the time specified.
	 * @param t
	 * @return
	 */
	public int getAtRisk(final double t) {
		int n = events.size();
		for (KaplanMeierEvent event : events) {
			if (event.getTimeToEvent() < t)
				n--;
			else
				break;
		}
		return n;
	}

	/**
	 * Get the number of events at a specified time (exactly).
	 * @param t
	 * @return
	 */
	public int getEventsAtTime(final double t) {
		int n = 0;
		for (KaplanMeierEvent event : events) {
			if (event.getTimeToEvent() < t)
				continue;
			if (event.getTimeToEvent() > t)
				break;
			if (!event.isCensored()) {
				n++;
			}
		}
		return n;
		
//		int n = 0;
//		for (KaplanMeierEvent event : events) {
//			if (event.getTimeToEvent() == t) {
//				//				if (Math.abs(event.getTimeToEvent() - t) < 0.00001) {
//				if (!event.isCensored())
//					n++;
//			}
//		}
//		return n;
	}

	//		public int getEventsPriorToTime(final double t) {
	//			int n = 0;
	//			for (KaplanMeierEvent event : events) {
	//				if (event.getTimeToEvent() <= t) {
	//					if (!event.isCensored())
	//						n++;
	//				}
	//				else
	//					break;
	//			}
	//			return n;
	//		}

	/**
	 * Get the number of events, either observed or censored.
	 * @return
	 */
	public int nEvents() {
		return events.size();
	}

	/**
	 * Get the number of observed (not censored) events.
	 * @return
	 */
	public int nObserved() {
		int n = 0;
		for (KaplanMeierEvent event : events) {
			if (!event.isCensored())
				n++;
		}
		return n;
	}

	/**
	 * Get the number of censored events.
	 * @return
	 */
	public int nCensored() {
		int n = 0;
		for (KaplanMeierEvent event : events) {
			if (event.isCensored())
				n++;
		}
		return n;
	}

	@Override
	public String toString() {
		return "Kaplan-Meier: " + name +", " + events.size() + " events (" + nObserved() + " observed, " + nCensored() + " censored)";
	}
	
	
	
	/**
	 * Simple class to store event time and censored flag.
	 */
	public static class KaplanMeierEvent implements Comparable<KaplanMeierEvent> {
		
		private double timeToEvent;
		private boolean isCensored;
		
		KaplanMeierEvent(final double timeToEvent, final boolean isCensored) {
			this.timeToEvent = timeToEvent;
			this.isCensored = isCensored;
		}
		
		/**
		 * Get the stored time to event (units are unspecified).
		 * @return
		 */
		public double getTimeToEvent() {
			return timeToEvent;
		}
		
		/**
		 * Returns true if the event should be considered right-censored.
		 * @return
		 */
		public boolean isCensored() {
			return isCensored;
		}

		@Override
		public int compareTo(KaplanMeierEvent event) {
			int comp = Double.compare(timeToEvent, event.timeToEvent);
			if (comp == 0)
				return Boolean.compare(isCensored, event.isCensored);
			return comp;
		}
		
		@Override
		public String toString() {
			return "KM Event: Time=" + timeToEvent + ", Censored=" + isCensored;
		}
		
	}

}