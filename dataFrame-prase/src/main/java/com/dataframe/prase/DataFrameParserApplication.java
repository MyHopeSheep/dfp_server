package com.dataframe.prase;

import com.dataframe.prase.bootstrap.SpringApplicationPortRetryLauncher;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataFrameParserApplication {

    public static void main(String[] args) {
        SpringApplicationPortRetryLauncher.forSpringApplication(DataFrameParserApplication.class).launch();
    }
}
