package com.dataframe.prase;

import com.dataframe.prase.startup.PortRetryLauncher;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DataFramePraseApplication {

    public static void main(String[] args) {
        PortRetryLauncher.forSpringApplication(DataFramePraseApplication.class).launch();
    }
}
