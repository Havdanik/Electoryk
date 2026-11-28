package com.elektoryk.scan.model;
public class GapAuditItem {
    public enum Severity { CRITICAL, WARNING, OPTIONAL, OK }
    public Severity severity=Severity.WARNING;
    public String area="";
    public String title="";
    public String detail="";
    public String action="";
    public GapAuditItem(){}
    public GapAuditItem(Severity s,String area,String title,String detail,String action){this.severity=s;this.area=area;this.title=title;this.detail=detail;this.action=action;}
}
