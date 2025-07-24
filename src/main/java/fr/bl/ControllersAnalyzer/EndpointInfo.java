package fr.bl.ControllersAnalyzer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EndpointInfo {
    private final String endpointMethodName;
	private final Set<String> dtoClasses = new HashSet<>();

    public EndpointInfo(String endpointMethodName) {
        this.endpointMethodName = endpointMethodName;
    }
    
    public void addDtoClass(String dtoClass) {
        dtoClasses.add(dtoClass);
    }
    
    public List<String> getDtoClasses() {
        return new ArrayList<>(dtoClasses);
    }
    
    public String getEndpointMethodName() {
		return endpointMethodName;
	}
}