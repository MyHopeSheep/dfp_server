package com.dataframe.prase.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.Desktop;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.util.List;

@Slf4j
public final class SpringApplicationPortRetryLauncher {

    private static final String LOCAL_ADDRESS = "127.0.0.1";
    private static final List<Integer> PORTS = List.of(8080, 8081, 8082);

    private final ApplicationStarter starter;
    private final BrowserLauncher browserLauncher;

    SpringApplicationPortRetryLauncher(ApplicationStarter starter) {
        this(starter, uri -> {
        });
    }

    SpringApplicationPortRetryLauncher(ApplicationStarter starter, BrowserLauncher browserLauncher) {
        this.starter = starter;
        this.browserLauncher = browserLauncher;
    }

    public static SpringApplicationPortRetryLauncher forSpringApplication(Class<?> primarySource) {
        return new SpringApplicationPortRetryLauncher(
                (port, address) -> {
                    SpringApplication application = new SpringApplication(primarySource);
                    return application.run(
                            "--server.address=" + address,
                            "--server.port=" + port);
                },
                SpringApplicationPortRetryLauncher::openHomePageInDefaultBrowser);
    }

    public ConfigurableApplicationContext launch() {
        RuntimeException lastPortFailure = null;
        for (int port : PORTS) {
            try {
                ConfigurableApplicationContext context = starter.start(port, LOCAL_ADDRESS);
                openHomePage(port);
                return context;
            } catch (RuntimeException exception) {
                if (!isPortInUse(exception)) {
                    throw exception;
                }
                lastPortFailure = exception;
                log.warn("本机端口 {} 已被占用，尝试下一个端口", port);
            }
        }
        throw lastPortFailure;
    }

    private void openHomePage(int port) {
        URI homePage = URI.create("http://" + LOCAL_ADDRESS + ":" + port + "/");
        try {
            browserLauncher.open(homePage);
        } catch (RuntimeException exception) {
            log.warn("服务已启动，但无法自动打开首页: url={}", homePage, exception);
        }
    }

    private static void openHomePageInDefaultBrowser(URI homePage) {
        try {
            if (!Desktop.isDesktopSupported()) {
                log.warn("当前运行环境不支持自动打开浏览器: url={}", homePage);
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                log.warn("当前运行环境不支持浏览器打开操作: url={}", homePage);
                return;
            }
            desktop.browse(homePage);
        } catch (IOException | RuntimeException exception) {
            log.warn("服务已启动，但无法自动打开首页: url={}", homePage, exception);
        }
    }

    private boolean isPortInUse(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PortInUseException || current instanceof BindException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @FunctionalInterface
    interface ApplicationStarter {
        ConfigurableApplicationContext start(int port, String address);
    }

    @FunctionalInterface
    interface BrowserLauncher {
        void open(URI homePage);
    }
}
