package fr.bl.ControllersAnalyzer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.json.simple.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ResultsAnalyzer {

    public static void analyzeDTOUsage(JSONObject controllerDtoJson) {
        Map<String, Integer> dtoFrequencyMap = new HashMap<>();

        for (Object controllerKey : controllerDtoJson.keySet()) {
            JSONObject methodMap = (JSONObject) controllerDtoJson.get(controllerKey);

            for (Object methodKey : methodMap.keySet()) {
                List<?> dtoArray = (List<?>) methodMap.get(methodKey);

                for (Object dtoObj : dtoArray) {
                    String dto = (String) dtoObj;
                    dtoFrequencyMap.put(dto, dtoFrequencyMap.getOrDefault(dto, 0) + 1);
                }
            }
        }

        // Print unique DTO count
        System.out.println("Number of unique DTOs: " + dtoFrequencyMap.size());

        // Most used DTO(s)
        int maxFrequency = Collections.max(dtoFrequencyMap.values());
        List<String> mostUsedDTOs = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : dtoFrequencyMap.entrySet()) {
            if (entry.getValue() == maxFrequency) {
                mostUsedDTOs.add(entry.getKey());
            }
        }

        System.out.println("\n== Most used DTO: " + mostUsedDTOs + " (Used " + maxFrequency + " times) ==");

        // Print top 10 DTOs with frequency
        System.out.println("\n== Frequently used DTOs ==\n");
        dtoFrequencyMap.entrySet()
            .stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(10)
            .forEach(entry ->
                System.out.println(entry.getKey() + " : " + entry.getValue())
            );

        // Create JSONObject for DTO frequencies
        JSONObject dtoFrequencyJson = new JSONObject();
        for (Map.Entry<String, Integer> entry : dtoFrequencyMap.entrySet()) {
            dtoFrequencyJson.put(entry.getKey(), entry.getValue());
        }

        // Write to file
        writeJsonToFile(dtoFrequencyJson, "dto_frequency.json");
    }

    private static void writeJsonToFile(JSONObject json, String filename) {
        try (FileWriter file = new FileWriter(filename)) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(file, json);
        } catch (IOException e) {
            System.err.println("Error writing DTO frequency JSON file: " + e.getMessage());
        }
    }
}