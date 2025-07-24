package fr.bl.ControllersAnalyzer;

import java.util.ArrayList;
import java.util.List;

public class ControllerInfo {
    private final String fullyQualifiedName;
    private final List<EndpointInfo> endpoints = new ArrayList<>();

    public ControllerInfo(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public void addEndpoint(EndpointInfo endpoint) {
        endpoints.add(endpoint);
    }
    
    public List<EndpointInfo> getEndpoints() {
        return endpoints;
    }
}
