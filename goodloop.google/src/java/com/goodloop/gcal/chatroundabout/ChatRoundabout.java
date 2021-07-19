package com.goodloop.gcal.chatroundabout;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.goodloop.gcal.GCalClient;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.winterwell.utils.Printer;
import com.winterwell.utils.Utils;
import com.winterwell.utils.io.CSVReader;

public class ChatRoundabout {

	public static void main(String[] args) throws IOException {
		// TODO use AMain and have a running service which goes each Monday
		new ChatRoundabout().run();
	}
	
	private ArrayList<String> emailList() {
		ArrayList<String> emailList = new ArrayList<String>();
		
		CSVReader r = new CSVReader(new File("data/staff.csv"));
		for (String[] row : r) {
			if (row[4].equals("Employee")) {
				String name = row[0];
				String office = row[2];
				// Catch Dan A
				String firstName;
				if (name.equals("Daniel Appel")) {
					firstName = "sysadmin";
				} else if (name.equals("Natasha Taylor")) {
					firstName = "tash";
				} else if (name.equals("Abdikarim Mohamed")) {
					firstName = "karim";
				} else {
					firstName = name.split(" ")[0];
				}
				String email = firstName.toLowerCase()+"@good-loop.com";
				emailList.add(email + "\t" + office);
			}
		}
		return emailList;
	}
	
	private ArrayList<String> checkEvent(ArrayList<String> emailList, LocalDate nextFriday) {
		
		// Restrict events around the date of the meeting
		
		// Only getting events 1 days before and after next Friday to try to catch long holidays but not getting too many event results		
		String startString = nextFriday.minusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME);
        DateTime start = DateTime.parseRfc3339(startString);
    	String endString = nextFriday.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME);
        DateTime end = DateTime.parseRfc3339(endString);
		
		GCalClient client = new GCalClient();
		
		ArrayList<String> filerOutEmail = new ArrayList<String>();
		for (String i : emailList) {
			String email = i.split("\t")[0];
			String office = i.split("\t")[1];
			System.out.println(email);
			
			List<Event> allEvents = client.getEvents(email, start, end);

			for (Event event : allEvents) {
	
				String eventItem = event.toString().toLowerCase();
				
				if (eventItem.contains("holiday") || eventItem.contains("chat-roundabout")) {
					boolean formatIsDate = event.getStart().getDate() != null;
					LocalDate startDate = (formatIsDate ? LocalDate.parse(event.getStart().getDate().toString()) : LocalDate.parse(event.getStart().getDateTime().toString().substring(0, 10)));
					LocalDate endDate = (formatIsDate ? LocalDate.parse(event.getEnd().getDate().toString()) : LocalDate.parse(event.getEnd().getDateTime().toString().substring(0, 10)).plusDays(1));
					
					List<LocalDate> holiDays = startDate.datesUntil(endDate).collect(Collectors.toList());
					System.out.println("Holidays: " + holiDays);
					
					if (email.equals("daniel@good-loop.com")) {
						System.out.print("");
					}
					if (holiDays.contains(nextFriday)) {
						filerOutEmail.add(email + "\t" + office);
					}
				}
			}
		}
		return filerOutEmail;
	}
	
	private ArrayList<ArrayList<String>> getRandomPairs(ArrayList<String> smallOffice, ArrayList<String> largeOffice) {
		ArrayList<ArrayList<String>> randomPairs = new ArrayList<ArrayList<String>>();
		Random rand = new Random();
		
		for (String pairEmail : smallOffice) {
			String randomEmail = largeOffice.get(rand.nextInt(largeOffice.size()));
			largeOffice.remove(randomEmail);
			ArrayList<String> pair = new ArrayList<String>();
			pair.add(pairEmail);
			pair.add(randomEmail);
			randomPairs.add(pair);
		}
		
		System.out.println("Poor guys who won't have 121 this week: " + largeOffice);
		
		return randomPairs;
	}
	
    public void Make1to1(String email1, String email2, LocalDate nextFriday) throws IOException {
        GCalClient gcc = new GCalClient();
        Calendar person1 = gcc.getCalendar(email1);
        Calendar person2 = gcc.getCalendar(email2);

        String eventDesc = "#Chat-Roundabout " + Utils.getNonce();

        DateTime startDateTime = new DateTime(nextFriday.toString() + "T11:30:00.00Z");
        DateTime endDateTime = new DateTime(nextFriday.toString() + "T11:40:00.00Z");
        String eventTimeDesc = "From " + startDateTime.toString() + " to " + endDateTime.toString();
        EventAttendee[] attendees = new EventAttendee[] {
            new EventAttendee().setEmail(email1)
                .setResponseStatus("tentative"),
            new EventAttendee().setEmail(email2)
                .setResponseStatus("tentative"),
        };
        String eventAttendeeDesc = "Attending: " + attendees.toString();
        String eventRemindersDesc = "Sending reminders via email (10 minutes) and popup (1 minute)";
        String calendarId = person1.getId(); // "primary";
        Printer.out("Adding to calendar " + calendarId + ": " + eventDesc + " " + eventTimeDesc + " " + eventAttendeeDesc + " " + eventRemindersDesc);
    }
	
	public void Make1to1ForReal(String email1, String email2, LocalDate nextFriday) throws IOException {
		GCalClient gcc = new GCalClient();
		Calendar person1 = gcc.getCalendar(email1);
		
		System.out.println(person1);
		Calendar person2 = gcc.getCalendar(email2);
		System.out.println(person2);		
		
		Event event = new Event()
	    .setSummary("#Chat-Roundabout "+Utils.getNonce())
	    .setDescription("A lovely event")
	    ;

		DateTime startDateTime = new DateTime(nextFriday.toString() + "T11:30:00.00Z");
		EventDateTime start = new EventDateTime()
		    .setDateTime(startDateTime)
		    .setTimeZone("GMT");
		event.setStart(start);

		DateTime endDateTime = new DateTime(nextFriday.toString() + "T11:40:00.00Z");
		EventDateTime end = new EventDateTime()
		    .setDateTime(endDateTime)
		    .setTimeZone("GMT");
		event.setEnd(end);

		// String[] recurrence = new String[] {"RRULE:FREQ=DAILY;COUNT=2"};
		// event.setRecurrence(Arrays.asList(recurrence));

		EventAttendee[] attendees = new EventAttendee[] {
		    new EventAttendee().setEmail(email1)
		    	.setResponseStatus("tentative"),
		    new EventAttendee().setEmail(email2)
		    	.setResponseStatus("tentative"),
		};
		event.setAttendees(Arrays.asList(attendees));

		EventReminder[] reminderOverrides = new EventReminder[] {
		    new EventReminder().setMethod("email").setMinutes(10),
		    new EventReminder().setMethod("popup").setMinutes(1),
		};
		Event.Reminders reminders = new Event.Reminders()
		    .setUseDefault(false)
		    .setOverrides(Arrays.asList(reminderOverrides));
		event.setReminders(reminders);

		String calendarId = person1.getId(); // "primary";
		Event event2 = gcc.addEvent(calendarId, event, false, true);
		
		Printer.out(event2.toPrettyString());
	}
	
	private void run() throws IOException {
		
		// Get a list of email
		ArrayList<String> emailList = emailList();
		
		// Get 121 Date (Next Friday)
		LocalDate nextFriday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
		System.out.println("Next Friday is: " + nextFriday);
		
		// Filter out if on holiday or already have a 121
		for (String holidayEmails : checkEvent(emailList, nextFriday)) {
			System.out.println(holidayEmails + " is in hoilday.");
			emailList.remove(holidayEmails);
		}
		
		// Separate Edinburgh and London team into two list
		ArrayList<String> edinburghEmails = new ArrayList<String>();
		ArrayList<String> londonEmails = new ArrayList<String>();
		
		for (String i : emailList) {
			String email = i.split("\t")[0];
			String office = i.split("\t")[1];
			if (office.equals("Edinburgh")) {
				edinburghEmails.add(email);
			} else {
				londonEmails.add(email);
			}
		}
		
		System.out.println("Edinburgh's team size today: " + edinburghEmails.size());
		System.out.println("London's team size today: " + londonEmails.size());
		
		// Logic: which office is larger
		boolean e2l = (edinburghEmails.size() > londonEmails.size());
		
		// Random pairings
		ArrayList<ArrayList<String>> randomPairs = new ArrayList<ArrayList<String>>();
		randomPairs = e2l ? getRandomPairs(londonEmails, edinburghEmails) : getRandomPairs(edinburghEmails, londonEmails);
		
		System.out.println(randomPairs);
		
		// Make events
		for (ArrayList<String> roundaboutPairs : randomPairs) {
			
			Make1to1(roundaboutPairs.get(0), roundaboutPairs.get(1), nextFriday);
		}
	}
	
}
