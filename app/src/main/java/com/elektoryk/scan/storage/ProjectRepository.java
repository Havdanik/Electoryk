package com.elektoryk.scan.storage;

import com.elektoryk.scan.model.ElectricalProject;
import java.util.List;

public interface ProjectRepository {
    List<ElectricalProject> list();
    ElectricalProject get(String id);
    void save(ElectricalProject project);
    void delete(String id);
}
