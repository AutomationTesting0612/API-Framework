package com.api.framework.testing.model;

public class SwaggerDocValue {

    public static final String REPORT="{}";

    public static final String DATALIST= "[\n" +
            "  {\n" +
            "    \"baseUrl\": \"http://localhost:8095/\",\n" +
            "    \"endpoint\": \"employees\",\n" +
            "    \"mappingType\": \"POST\",\n" +
            "    \"header\": {\n" +
            "      \"content-Type\": \"application/json\"\n" +
            "    },\n" +
            "    \"scenario\": {\n" +
            "      \"name\": \"API\",\n" +
            "      \"description\": \"TESTING THE API\",\n" +
            "      \"code\": \"200\",\n" +
            "      \"datasets\": [\n" +
            "        {\n" +
            "          \"request_body\": {\n" +
            "            \"id\": 4,\n" +
            "            \"name\": \"Akhil14\",\n" +
            "            \"age\": 30,\n" +
            "            \"address\": \"testing12\"\n" +
            "          },\n" +
            "          \"desired_status\": \"200\",\n" +
            "          \"desired_outcome\": \"{\\n  \\\"name\\\": \\\"Akhil\\\",\\n  \\\"age\\\": \\\"32\\\",\\n  \\\"address\\\": \\\"testing\\\"\\n}\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "]";


}
