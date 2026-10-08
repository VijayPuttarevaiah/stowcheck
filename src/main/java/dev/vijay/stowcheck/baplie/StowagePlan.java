package dev.vijay.stowcheck.baplie;

import java.util.ArrayList;
import java.util.List;

/** A parsed BAPLIE message: the vessel header plus every occupied stowage cell. */
public class StowagePlan {

    // Interchange envelope (UNB/UNZ) and message envelope (UNH/UNT)
    String sender;
    String recipient;
    String interchangeRef;
    String interchangeTrailerRef;
    Integer interchangeTrailerCount;
    int messageCount;

    String messageType;
    String messageVersion;
    String messageRef;
    String messageTrailerRef;
    Integer declaredSegmentCount;
    int actualSegmentCount;

    /** BGM message function: "9" original, "5" replace, "22" final ... */
    String messageFunction;

    String voyage;
    String carrier;
    String vesselId;
    String vesselName;
    String departurePort;
    String nextPortOfCall;

    final List<StowedUnit> units = new ArrayList<>();

    public String sender() { return sender; }
    public String recipient() { return recipient; }
    public String interchangeRef() { return interchangeRef; }
    public String interchangeTrailerRef() { return interchangeTrailerRef; }
    public Integer interchangeTrailerCount() { return interchangeTrailerCount; }
    public int messageCount() { return messageCount; }
    public String messageType() { return messageType; }
    public String messageVersion() { return messageVersion; }
    public String messageRef() { return messageRef; }
    public String messageTrailerRef() { return messageTrailerRef; }
    public Integer declaredSegmentCount() { return declaredSegmentCount; }
    public int actualSegmentCount() { return actualSegmentCount; }
    public String messageFunction() { return messageFunction; }
    public String voyage() { return voyage; }
    public String carrier() { return carrier; }
    public String vesselId() { return vesselId; }
    public String vesselName() { return vesselName; }
    public String departurePort() { return departurePort; }
    public String nextPortOfCall() { return nextPortOfCall; }
    public List<StowedUnit> units() { return units; }
}
