package com.elektoryk.scan.model;
import java.util.*;
public class ProjectGapAuditReport {
    public int score;
    public final List<GapAuditItem> items=new ArrayList<>();
    public int count(GapAuditItem.Severity s){int n=0;for(GapAuditItem i:items)if(i.severity==s)n++;return n;}
    public boolean hasCritical(){return count(GapAuditItem.Severity.CRITICAL)>0;}
}
