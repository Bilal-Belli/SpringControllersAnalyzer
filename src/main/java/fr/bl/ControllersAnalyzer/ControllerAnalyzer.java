package fr.bl.ControllersAnalyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import org.json.simple.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ControllerAnalyzer {
    
    // Controllers specific annotations
    private static final Set<String> CONTROLLER_CLASS_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "Controller", "RestController", "Path", "ApplicationPath", "Service"
    ));
    
    // End point mapping specific annotations
    private static final Set<String> ENDPOINT_MAPPING_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", 
            "RequestMapping", "ResponseBody"
    ));

    public static void main(String[] args) {
        String directoryPath;
        
        if (args.length > 0) {
            directoryPath = args[0];
        } else {
        	System.err.println("Path not specified");
        	return;
        }
        
        // Generate the JSON with controller-endpoint-DTO structure
        JSONObject controllerDtoJson = analyzeProject(directoryPath);
        
        // Write analyzing results to a JSON file
        writeJsonToFile(controllerDtoJson, "controller_dto_mapping.json");
        
        System.out.println("JSON file generated successfully");
    }

    public static JSONObject analyzeProject(String directoryPath) {
        JSONObject controllersJson = new JSONObject();
        try {
            List<File> javaFiles = findJavaFiles(new File(directoryPath));
            CombinedTypeSolver typeSolver = new CombinedTypeSolver();
            typeSolver.add(new ReflectionTypeSolver());
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            JavaParser javaParser = new JavaParser(new ParserConfiguration().setSymbolResolver(symbolSolver));

            Map<String, ClassOrInterfaceDeclaration> interfacesMap = new HashMap<>();
            Map<String, ControllerInfo> controllers = new HashMap<>();
            
            // First pass - identify controllers
            for (File file : javaFiles) {
                try {
                    CompilationUnit cu = javaParser.parse(file).getResult()
                            .orElseThrow(() -> new IOException("Parsing failed"));
                    
                    // Find interfaces (necessary to check annotated endpoints in interfaces rather than in classes)
                    collectInterfaces(cu, interfacesMap);
                    
                    // Find and analyze controllers
                    findControllers(cu, controllers, interfacesMap);
                    
                } catch (IOException e) {
                    System.err.println("Error parsing file: " + file.getPath() + " - " + e.getMessage());
                }
            }
            
            // Convert controller info to JSON structure
            for (ControllerInfo controller : controllers.values()) {
                JSONObject endpointsJson = new JSONObject();
                
                for (EndpointInfo endpoint : controller.getEndpoints()) {
                    endpointsJson.put(endpoint.getEndpointMethodName(), endpoint.getDtoClasses());
                }
                
                controllersJson.put(controller.getFullyQualifiedName(), endpointsJson);
            }
            
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
        
        return controllersJson;
    }
    
    private static void findControllers(CompilationUnit cu,
            Map<String, ControllerInfo> controllers,
            Map<String, ClassOrInterfaceDeclaration> interfacesMap) {
        // Get the package name if available
        String packageName = cu.getPackageDeclaration().isPresent() ? 
                cu.getPackageDeclaration().get().getNameAsString() + "." : "";
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            String fullName = packageName + classDecl.getNameAsString();
            
            // Check if it's a controller class
            if (isControllerClass(classDecl)) {
                ControllerInfo controllerInfo = new ControllerInfo(fullName);
                controllers.put(fullName, controllerInfo);
                
                // Process "annotated classes" methods to find endpoints
                // processing methods in the class itself
                classDecl.getMethods().forEach(method -> {
                    if (isEndpoint(method)) {
                        EndpointInfo endpointInfo = new EndpointInfo(method.getNameAsString());
                        String returnType = method.getType().asString();
                        // consider the method return type in list of DTOs
                        if (!returnType.equals("void")) {
                            endpointInfo.addDtoClass(returnType);
                        }
                        // consider the parameters types in list of DTOs
                        method.getParameters().forEach(param -> {
                            endpointInfo.addDtoClass(param.getType().asString());
                        });
                        controllerInfo.addEndpoint(endpointInfo);
                    }
                });

                // Check implemented interfaces for annotated methods
                classDecl.getImplementedTypes().forEach(implType -> {
                    resolveInterface(implType, interfacesMap)
                        .ifPresent(intf -> {
                            intf.getMethods().forEach(interfaceMethod -> {
                                if (isEndpoint(interfaceMethod)) {
                                    EndpointInfo info = new EndpointInfo(interfaceMethod.getNameAsString());
                                    String ret = interfaceMethod.getType().asString();
                                    if (!"void".equals(ret)) info.addDtoClass(ret);
                                    controllerInfo.addEndpoint(info);
                                }
                            });
                        });
                });
            }
        });
    }
    
    private static boolean isControllerClass(ClassOrInterfaceDeclaration classDecl) {
        boolean hasControllerAnnotation = hasAnyAnnotation(classDecl, CONTROLLER_CLASS_ANNOTATIONS);
        
        if (hasControllerAnnotation) {
            // If it's annotated with @Service, validate it contains at least one endpoint method
            boolean isService = hasAnyAnnotation(classDecl, Collections.singleton("Service"));
            if (isService) {
                // Return true only if it has at least one method with endpoint mapping
                return classDecl.getMethods().stream().anyMatch(ControllerAnalyzer::isEndpoint);
            }
            return true;
        }
        return false;
    }
    
    private static boolean isEndpoint(MethodDeclaration method) {
        return hasAnyAnnotation(method, ENDPOINT_MAPPING_ANNOTATIONS);
    }
    
    private static boolean hasAnyAnnotation(NodeWithAnnotations<?> node, Set<String> annotationNames) {
        NodeList<AnnotationExpr> annotations = node.getAnnotations();
        for (AnnotationExpr annotation : annotations) {
            String name = annotation.getNameAsString();
            
            // Handle simply defined annotation names
            // For example : "@RestController" annotation
            if (annotationNames.contains(name)) {
                return true;
            }
            
            // Check for the annotation name without prefix
            // For example : "@org.springframework.web.bind.annotation.RestController" annotation that uses the full path
            if (name.contains(".")) {
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                if (annotationNames.contains(simpleName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(directory, javaFiles);
        return javaFiles;
    }

    private static void findJavaFilesRecursive(File directory, List<File> javaFiles) {
        if (!directory.isDirectory()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    private static void writeJsonToFile(JSONObject json, String filename) {
        try (FileWriter file = new FileWriter(filename)) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(file, json);
        } catch (IOException e) {
            System.err.println("Error writing JSON file: " + e.getMessage());
        }
    }

    private static Optional<ClassOrInterfaceDeclaration> resolveInterface(
            ClassOrInterfaceType implType,
            Map<String, ClassOrInterfaceDeclaration> interfacesMap) {

        String simple = implType.getNameAsString();
        String qualifier = implType.getScope().map(Node::toString).orElse("");
        return Optional.ofNullable(
                interfacesMap.getOrDefault(
                    qualifier.isEmpty() ? simple : qualifier + "." + simple,
                    interfacesMap.get(simple)
                )
        );
    }

    private static void collectInterfaces(CompilationUnit cu, Map<String, ClassOrInterfaceDeclaration> interfacesMap) {
        String pkg = cu.getPackageDeclaration()
                       .map(pd -> pd.getNameAsString() + ".")
                       .orElse("");

        cu.findAll(ClassOrInterfaceDeclaration.class).stream()
           .filter(ClassOrInterfaceDeclaration::isInterface)
           .forEach(intf -> {
               interfacesMap.put(intf.getNameAsString(), intf);
               interfacesMap.put(pkg + intf.getNameAsString(), intf);
           });
    }
}