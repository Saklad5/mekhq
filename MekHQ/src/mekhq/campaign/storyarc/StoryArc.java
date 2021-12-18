/*
 * StoryArc.java
 *
 * Copyright (c) 2020 - The MegaMek Team. All Rights Reserved
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ.  If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.campaign.storyarc;

import megamek.common.annotations.Nullable;
import megamek.common.event.Subscribe;
import megamek.common.util.sorter.NaturalOrderComparator;
import mekhq.*;
import mekhq.campaign.event.NewDayEvent;
import mekhq.campaign.event.PersonChangedEvent;
import mekhq.campaign.event.ScenarioResolvedEvent;
import mekhq.campaign.Campaign;
import mekhq.campaign.event.TransitCompleteEvent;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.storyarc.storyevent.DateReachedStoryEvent;
import mekhq.campaign.storyarc.storyevent.PersonKilledStoryEvent;
import mekhq.campaign.storyarc.storyevent.ScenarioStoryEvent;
import mekhq.campaign.storyarc.storyevent.TravelStoryEvent;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.FilenameFilter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Story Arc class manages a given story arc campaign
 */
public class StoryArc implements MekHqXmlSerializable {

    private String title;
    private String details;
    private String description;

    private Campaign campaign;

    /** Can this story arc be added to existing campaign or does it need to start fresh? **/
    private boolean startNew;

    /** A UUID for the initial event in this track  - can be null **/
    private UUID startingEventId;

    /** A hash of all possible StoryEvents in this StoryArc, referenced by UUID **/
    private Map<UUID, StoryEvent> storyEvents;

    /** A hash of possible personalities that the player might interact with in this story arc **/
    private Map<UUID, Personality> personalities;

    /**
     * a hash of custom string variables that the creator might specify with a string key
     */
    private Map<String, String> customStringVariables;

    /** directory path to the initial campaign data for this StoryArc - can be null **/
    private String initCampaignPath;

    /** directory path to this story arc **/
    private String directoryPath;

    /** a hash map of replacements for tokens in the narrative strings */
    private static Map<String, String> replacementTokens;

    protected static final String NL = System.lineSeparator();

    public StoryArc() {
        startNew = true;
        storyEvents =  new LinkedHashMap<>();
        personalities = new LinkedHashMap<>();
        customStringVariables = new LinkedHashMap<>();
    }

    public void setCampaign(Campaign c) { this.campaign = c; }

    public Campaign getCampaign() { return campaign; }

    private void setTitle(String t) { this.title = t; }

    public String getTitle() { return this.title; }

    public String getDetails() { return details; }

    private void setDetails(String d) { this.details = d; }

    public String getDescription() { return this.description; }

    private void setDescription(String d) { this.description = d; }

    private void setStartNew(Boolean b) { this.startNew = b; }

    private void setStartingEventId(UUID u) { this.startingEventId = u; }

    private UUID getStartingEventId() { return startingEventId; }

    private void setInitCampaignPath(String s) { this.initCampaignPath =s; }

    public File getInitCampaignFile() {
        if(null == initCampaignPath) {
            return null;
        }
        return new File(initCampaignPath);
    }

    public void setDirectoryPath(String p) { this.directoryPath = p; }

    public String getDirectoryPath() { return directoryPath; }

    public StoryEvent getStoryEvent(UUID id) {
        if (id == null) {
            return null;
        }
        return storyEvents.get(id);
    }

    public Personality getPersonality(UUID id) {
        if (id == null) {
            return null;
        }
        Personality p = personalities.get(id);
        p.updatePersonalityFromCampaign(campaign);
        return p;
    }

    public void addCustomStringVariable(String key, String value) {
        customStringVariables.put(key, value);
    }

    public String getCustomStringVariable(String key) {
        return customStringVariables.get(key);
    }

    public void begin() {
        MekHQ.registerHandler(this);
        getStoryEvent(getStartingEventId()).startEvent();
    }

    public void initializeDataDirectories() {
        MHQStaticDirectoryManager.initializeUserStoryPortraits(getDirectoryPath() + "/data/images/portraits");
        MHQStaticDirectoryManager.initializeUserStorySplash(getDirectoryPath() + "/data/images/storysplash");

    }

    private ScenarioStoryEvent findStoryEventByScenarioId(int scenarioId) {
        for (Map.Entry<UUID, StoryEvent> entry : storyEvents.entrySet()) {
            if (entry.getValue() instanceof ScenarioStoryEvent) {
                ScenarioStoryEvent storyEvent = (ScenarioStoryEvent) entry.getValue();
                if (null != storyEvent.getScenario() && storyEvent.getScenario().getId() == scenarioId) {
                    return storyEvent;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return getTitle();
    }

    //region EventHandlers
    @Subscribe
    public void handleScenarioResolved(ScenarioResolvedEvent ev) {
        //search through ScenarioStoryEvents for a match and if so complete it
        ScenarioStoryEvent storyEvent = findStoryEventByScenarioId(ev.getScenario().getId());
        if(null != storyEvent && storyEvent.isActive()) {
            storyEvent.completeEvent();
        }
    }

    @Subscribe
    public void handleTransitComplete(TransitCompleteEvent ev) {
        //search through StoryEvents for a matching TravelStoryEvent
        TravelStoryEvent storyEvent;
        for (Map.Entry<UUID, StoryEvent> entry : storyEvents.entrySet()) {
            if (entry.getValue() instanceof TravelStoryEvent) {
                 storyEvent = (TravelStoryEvent) entry.getValue();
                 if(ev.getLocation().getCurrentSystem().getId().equals(storyEvent.getDestinationId()) &&
                         storyEvent.isActive()) {
                     storyEvent.completeEvent();
                     break;
                }

            }
        }
    }

    @Subscribe
    public void handleNewDay(NewDayEvent ev) {
        //search through StoryEvents for a matching DateReachedStoryEvent
        DateReachedStoryEvent storyEvent;
        for (Map.Entry<UUID, StoryEvent> entry : storyEvents.entrySet()) {
            if (entry.getValue() instanceof DateReachedStoryEvent) {
                storyEvent = (DateReachedStoryEvent) entry.getValue();
                if(null != storyEvent.getDate() && ev.getCampaign().getLocalDate().equals(storyEvent.getDate())) {
                    storyEvent.startEvent();
                    break;
                }

            }
        }
    }

    @Subscribe
    public void handlePersonChanged(PersonChangedEvent ev) {
        Person p = ev.getPerson();
        if(null != p && p.getStatus().isDead()) {
            PersonKilledStoryEvent storyEvent;
            for (Map.Entry<UUID, StoryEvent> entry : storyEvents.entrySet()) {
                if (entry.getValue() instanceof PersonKilledStoryEvent) {
                    storyEvent = (PersonKilledStoryEvent) entry.getValue();
                    if(p.getId().equals(storyEvent.getPersonId())) {
                        storyEvent.startEvent();
                        break;
                    }
                }
            }
        }

    }
    //endregion EventHandlers

    //region File I/O
    @Override
    public void writeToXml(PrintWriter pw1, int indent) {
        writeToXmlBegin(pw1, indent);
        writeToXmlEnd(pw1, indent);
    }

    protected void writeToXmlBegin(PrintWriter pw1, int indent) {
        String level = MekHqXmlUtil.indentStr(indent),
                level1 = MekHqXmlUtil.indentStr(indent + 1);

        StringBuilder builder = new StringBuilder(256);
        builder.append(level)
                .append("<storyArc>")
                .append(NL)
                .append(level1)
                .append("<title>")
                .append(title)
                .append("</title>")
                .append(NL)
                .append(level1)
                .append("<details>")
                .append(details)
                .append("</details>")
                .append(NL)
                .append(level1)
                .append("<description>")
                .append(description)
                .append("</description>")
                .append(NL)
                .append(level1)
                .append("<startNew>")
                .append(startNew)
                .append("</startNew>")
                .append(NL)
                .append(level1)
                .append("<startingEventId>")
                .append(startingEventId)
                .append("</startingEventId>")
                .append(NL)
                .append(level1)
                .append("<directoryPath>")
                .append(directoryPath)
                .append("</directoryPath>")
                .append(NL);
        pw1.print(builder.toString());

        pw1.println(MekHqXmlUtil.indentStr(indent+1)
                +"<storyEvents>");
        for (Map.Entry<UUID, StoryEvent> entry : storyEvents.entrySet()) {
            entry.getValue().writeToXml(pw1, indent+2);
        }
        pw1.println(MekHqXmlUtil.indentStr(indent+1)
                +"</storyEvents>");

        if(!personalities.isEmpty()) {
            pw1.println(MekHqXmlUtil.indentStr(indent + 1)
                    + "<personalities>");
            for (Map.Entry<UUID, Personality> entry : personalities.entrySet()) {
                entry.getValue().writeToXml(pw1, indent + 2);
            }
            pw1.println(MekHqXmlUtil.indentStr(indent + 1)
                    + "</personalities>");
        }

        if(!customStringVariables.isEmpty()) {
            pw1.println(MekHqXmlUtil.indentStr(indent + 1)
                    + "<customStringVariables>");
            for (Map.Entry<String, String> entry : customStringVariables.entrySet()) {
                pw1.println(MekHqXmlUtil.indentStr(indent + 2)
                        + "<customStringVariable>");
                pw1.println(MekHqXmlUtil.indentStr(indent + 3)
                        + "<key>"
                        + entry.getKey()
                        + "</key>");
                pw1.println(MekHqXmlUtil.indentStr(indent + 3)
                        + "<value>"
                        + entry.getValue()
                        + "</value>");
                pw1.println(MekHqXmlUtil.indentStr(indent + 2)
                        + "</customStringVariable>");
            }
            pw1.println(MekHqXmlUtil.indentStr(indent + 1)
                    + "</customStringVariables>");
        }


    }

    protected void writeToXmlEnd(PrintWriter pw1, int indent) {
        pw1.println(MekHqXmlUtil.indentStr(indent) + "</storyArc>");
    }

    protected void parseStoryEvents(NodeList nl, Campaign c) {
        try {
            for (int x = 0; x < nl.getLength(); x++) {
                final Node wn = nl.item(x);
                if (wn.getNodeType() != Node.ELEMENT_NODE ||
                        !wn.getNodeName().equals("storyEvent")) {
                    continue;
                }
                StoryEvent event = StoryEvent.generateInstanceFromXML(wn, c);
                if(null != event) {
                    event.setStoryArc(this);
                    storyEvents.put(event.getId(), event);
                }
            }
        } catch (Exception e) {
            LogManager.getLogger().error(e);
        }
    }

    protected void parsePersonalities(NodeList nl, Campaign c) {
        try {
            for (int x = 0; x < nl.getLength(); x++) {
                final Node wn = nl.item(x);
                if (wn.getNodeType() != Node.ELEMENT_NODE ||
                        !wn.getNodeName().equals("personality")) {
                    continue;
                }
                Personality personality = Personality.generateInstanceFromXML(wn, c);
                if(null != personality) {
                    personalities.put(personality.getId(), personality);
                }
            }
        } catch (Exception e) {
            LogManager.getLogger().error(e);
        }
    }

    protected void parseCustomStringVariables(NodeList nl, Campaign c) {
        try {
            for (int x = 0; x < nl.getLength(); x++) {
                final Node wn = nl.item(x);
                if (wn.getNodeType() != Node.ELEMENT_NODE ||
                        !wn.getNodeName().equals("customStringVariable")) {
                    continue;
                }
                parseCustomStringVariable(wn.getChildNodes(), c);
            }
        } catch (Exception e) {
            LogManager.getLogger().error(e);
        }
    }

    protected void parseCustomStringVariable(NodeList nl, Campaign c) {
        String key = null;
        String value = null;
        try {
            for (int x = 0; x < nl.getLength(); x++) {
                final Node wn = nl.item(x);
                if (wn.getNodeName().equals("key")) {
                    key = wn.getTextContent().trim();
                } else if (wn.getNodeName().equals("value")) {
                    value = wn.getTextContent().trim();
                }
            }
        } catch (Exception e) {
            LogManager.getLogger().error(e);
        }
        if(null != key && null != value) {
            addCustomStringVariable(key, value);
        }
    }

    public static @Nullable StoryArc parseFromXML(final NodeList nl, Campaign c) {
        final StoryArc storyArc = new StoryArc();
        try {
            for (int x = 0; x < nl.getLength(); x++) {
                final Node wn = nl.item(x);
                if (wn.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                switch (wn.getNodeName()) {
                    case "title":
                        storyArc.setTitle(wn.getTextContent().trim());
                        break;
                    case "details":
                        storyArc.setDetails(wn.getTextContent().trim());
                        break;
                    case "description":
                        storyArc.setDescription(wn.getTextContent().trim());
                        break;
                    case "startNew":
                        storyArc.setStartNew(Boolean.parseBoolean(wn.getTextContent().trim()));
                        break;
                    case "startingEventId":
                        storyArc.setStartingEventId(UUID.fromString(wn.getTextContent().trim()));
                        break;
                    case "directoryPath":
                        storyArc.setDirectoryPath(wn.getTextContent().trim());
                        break;
                    case "storyEvents":
                        storyArc.parseStoryEvents(wn.getChildNodes(), c);
                        break;
                    case "personalities":
                        storyArc.parsePersonalities(wn.getChildNodes(), c);
                        break;
                    case "customStringVariables":
                        storyArc.parseCustomStringVariables(wn.getChildNodes(), c);
                        break;


                    default:
                        break;
                }
            }
        } catch (Exception e) {
            LogManager.getLogger().error(e);
            return null;
        }
        return storyArc;
    }

    /**
     * @return a list of all of the story arcs in the default and userdata folders
     */
    public static List<StoryArc> getStoryArcs() {
        final List<StoryArc> arcs = loadStoryArcsFromDirectory(
                new File(MekHqConstants.STORY_ARC_DIRECTORY));
        arcs.addAll(loadStoryArcsFromDirectory(
                new File(MekHqConstants.USER_STORY_ARC_DIRECTORY)));
        final NaturalOrderComparator naturalOrderComparator = new NaturalOrderComparator();
        arcs.sort((p0, p1) -> naturalOrderComparator.compare(p0.toString(), p1.toString()));
        return arcs;
    }

    public static List<StoryArc> loadStoryArcsFromDirectory(final @Nullable File directory) {
        if ((directory == null) || !directory.exists() || !directory.isDirectory()) {
            return new ArrayList<>();
        }

        //get all the story arc directory names
        String[] arcDirectories = directory.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        final List<StoryArc> storyArcs = new ArrayList<>();
        for(String arcDirectoryName : arcDirectories) {
            //find the expected items within this story arc directory
            final File storyArcFile = new File(directory.getPath() + "/" +  arcDirectoryName + "/" + MekHqConstants.STORY_ARC_FILE);
            if(!storyArcFile.exists()) {
                continue;
            }
            final StoryArc storyArc = parseFromFile(storyArcFile);
            storyArc.setDirectoryPath(directory.getPath() + "/" +  arcDirectoryName);
            final File initCampaignFile = new File(directory.getPath() + "/" +  arcDirectoryName + "/" + MekHqConstants.STORY_ARC_CAMPAIGN_FILE);
            if (storyArc != null) {
                if(initCampaignFile.exists()) {
                    storyArc.setInitCampaignPath(initCampaignFile.getPath());
                }
                storyArcs.add(storyArc);
            }
        }

        return storyArcs;
    }

    public static @Nullable StoryArc parseFromFile(final @Nullable File file) {
        final Document xmlDoc;
        try (InputStream is = new FileInputStream(file)) {
            xmlDoc = MekHqXmlUtil.newSafeDocumentBuilder().parse(is);
        } catch (Exception e) {
            LogManager.getLogger().error(e);
            return null;
        }

        final Element element = xmlDoc.getDocumentElement();
        element.normalize();

        return parseFromXML(element.getChildNodes(), null);
    }

    //endregion File I/O

    private static void updateReplacementTokens(Campaign c) {
        if(null == replacementTokens) {
            replacementTokens = new LinkedHashMap<>();
        }
        Person commander = c.getSeniorCommander();
        if(null == commander) {
            //shouldn't happen unless there are no personnel, but just in case
            replacementTokens.put("@commanderRank", "rank(?)");
            replacementTokens.put("@commander", "commander(?)");

        } else {
            replacementTokens.put("@commanderRank", commander.getRankName());
            replacementTokens.put("@commander", commander.getFullTitle());
        }
    }

    /**
     * This will replace tokens in narrative text
     * @param text
     * @return
     */
    public static String replaceTokens(String text, Campaign c) {

        updateReplacementTokens(c);

        Pattern pattern;
        Matcher matcher;
        for (Map.Entry<String, String> replacement : replacementTokens.entrySet()) {
            pattern = Pattern.compile(replacement.getKey());
            matcher = pattern.matcher(text);
            text = matcher.replaceAll(replacement.getValue());
        }

        return text;
    }

}
