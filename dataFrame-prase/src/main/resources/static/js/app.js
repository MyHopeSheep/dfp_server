(() => {
    const MAX_FILE_SIZE = 20 * 1024 * 1024;
    const STORAGE_KEY = "dfp.selectedConfigKey";
    const XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    const state = {
        serviceReady: false,
        configs: [],
        configKey: "",
        selectedFile: null,
        parsing: false
    };

    const elements = {
        serviceStatus: document.getElementById("service-status"),
        configSelect: document.getElementById("config-key"),
        refreshConfigs: document.getElementById("refresh-configs"),
        configSummary: document.getElementById("config-summary"),
        configMark: document.getElementById("config-step-mark"),
        fileInput: document.getElementById("csv-file"),
        fileSummary: document.getElementById("file-summary"),
        fileMark: document.getElementById("file-step-mark"),
        parseSummary: document.getElementById("parse-summary"),
        parseButton: document.getElementById("parse-button"),
        feedback: document.getElementById("feedback")
    };

    function initialize() {
        elements.configSelect.addEventListener("change", onConfigChanged);
        elements.refreshConfigs.addEventListener("click", refreshConfigs);
        elements.fileInput.addEventListener("change", onFileChanged);
        elements.parseButton.addEventListener("click", parseAndDownload);
        refreshHealth();
        refreshConfigs();
        render();
    }

    async function refreshHealth() {
        elements.serviceStatus.textContent = "正在检查服务状态…";
        elements.serviceStatus.className = "service-status service-status--checking";
        try {
            const response = await fetch("/dfp/health");
            if (!response.ok) {
                throw new Error("健康检查失败");
            }
            const health = await response.json();
            state.serviceReady = health.status === "UP";
            elements.serviceStatus.textContent = state.serviceReady
                ? `● 服务已就绪 · 端口 ${health.port} · 版本 ${health.version}`
                : "服务尚未就绪";
            elements.serviceStatus.className = state.serviceReady
                ? "service-status service-status--ready"
                : "service-status service-status--error";
        } catch (error) {
            state.serviceReady = false;
            elements.serviceStatus.textContent = "服务连接失败，请确认黑窗口中的服务仍在运行。";
            elements.serviceStatus.className = "service-status service-status--error";
        }
        render();
    }

    async function refreshConfigs() {
        const previousKey = state.configKey;
        elements.configSelect.disabled = true;
        elements.refreshConfigs.disabled = true;
        elements.configSelect.replaceChildren(createOption("", "正在读取配置…"));
        try {
            const response = await fetch("/dfp/configs");
            if (!response.ok) {
                throw new Error(await readErrorMessage(response));
            }
            const configs = await response.json();
            if (!Array.isArray(configs) || configs.length === 0) {
                throw new Error("未读取到可用的遥控器配置");
            }
            state.configs = configs;
            const savedKey = localStorage.getItem(STORAGE_KEY) || "";
            const retainedKey = selectExistingKey(previousKey, savedKey, configs);
            state.configKey = retainedKey;
            renderConfigOptions();
            if (previousKey && !retainedKey) {
                showFeedback("warning", "当前配置已不存在，请重新选择。");
            }
        } catch (error) {
            state.configs = [];
            state.configKey = "";
            elements.configSelect.replaceChildren(createOption("", "配置读取失败"));
            showFeedback("error", error.message || "配置文件读取失败，请保存正确配置后重试。");
        } finally {
            elements.configSelect.disabled = false;
            elements.refreshConfigs.disabled = false;
            render();
        }
    }

    function selectExistingKey(previousKey, savedKey, configs) {
        const availableKeys = new Set(configs.map(config => config.key));
        if (availableKeys.has(previousKey)) {
            return previousKey;
        }
        if (availableKeys.has(savedKey)) {
            return savedKey;
        }
        return "";
    }

    function renderConfigOptions() {
        const options = [createOption("", "请选择遥控器配置")];
        for (const config of state.configs) {
            options.push(createOption(config.key, config.name));
        }
        elements.configSelect.replaceChildren(...options);
        elements.configSelect.value = state.configKey;
    }

    function createOption(value, label) {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label;
        return option;
    }

    function onConfigChanged() {
        state.configKey = elements.configSelect.value;
        if (state.configKey) {
            localStorage.setItem(STORAGE_KEY, state.configKey);
        } else {
            localStorage.removeItem(STORAGE_KEY);
        }
        clearFeedback();
        render();
    }

    function onFileChanged() {
        const file = elements.fileInput.files[0] || null;
        state.selectedFile = null;
        clearFeedback();
        if (!file) {
            render();
            return;
        }
        if (!file.name.toLowerCase().endsWith(".csv")) {
            showFeedback("warning", "只支持上传 .csv 文件。");
        } else if (file.size === 0) {
            showFeedback("warning", "上传的 CSV 文件不能为空。");
        } else if (file.size > MAX_FILE_SIZE) {
            showFeedback("warning", "CSV 文件不能超过 20MB。");
        } else {
            state.selectedFile = file;
        }
        render();
    }

    async function parseAndDownload() {
        if (!canSubmit()) {
            showFeedback("warning", "请先选择配置和 CSV 文件。");
            return;
        }
        state.parsing = true;
        clearFeedback();
        render();
        try {
            const formData = new FormData();
            formData.append("file", state.selectedFile);
            formData.append("configKey", state.configKey);
            const response = await fetch("/dfp/csvParse", {
                method: "POST",
                body: formData
            });
            if (!response.ok) {
                throw new Error(await readErrorMessage(response));
            }
            const contentType = response.headers.get("Content-Type") || "";
            if (!contentType.toLowerCase().startsWith(XLSX_CONTENT_TYPE)) {
                throw new Error("无法识别服务响应，请稍后重试。");
            }
            const fileName = extractDownloadName(response.headers.get("Content-Disposition"))
                || fallbackDownloadName(state.selectedFile.name);
            triggerDownload(await response.blob(), fileName);
            showFeedback("success", `已开始下载“${fileName}”。`);
        } catch (error) {
            showFeedback("error", error.message || "服务连接失败，请确认黑窗口中的服务仍在运行。");
        } finally {
            state.parsing = false;
            render();
        }
    }

    function canSubmit() {
        return state.serviceReady && Boolean(state.configKey) && state.selectedFile !== null && !state.parsing;
    }

    function render() {
        const selectedConfig = state.configs.find(config => config.key === state.configKey);
        renderStep(elements.configMark, selectedConfig, "①");
        renderStep(elements.fileMark, state.selectedFile, "②");
        elements.configSummary.textContent = selectedConfig
            ? `已选择：${selectedConfig.name}`
            : "请选择本次 CSV 解析使用的遥控器配置。";
        elements.fileSummary.textContent = state.selectedFile
            ? `已选择：${state.selectedFile.name}（${formatFileSize(state.selectedFile.size)}）`
            : "仅支持 CSV 文件，单个文件最大 20MB。";
        elements.parseSummary.textContent = selectedConfig && state.selectedFile
            ? `将使用：${selectedConfig.name} · ${state.selectedFile.name}`
            : "请先选择配置和 CSV 文件。";
        elements.parseButton.disabled = !canSubmit();
        elements.parseButton.textContent = state.parsing
            ? "正在解析，请勿重复提交…"
            : canSubmit()
                ? "开始解析并下载"
                : "请先选择配置和 CSV 文件";
        elements.configSelect.disabled = state.parsing || state.configs.length === 0;
        elements.refreshConfigs.disabled = state.parsing;
        elements.fileInput.disabled = state.parsing;
    }

    function renderStep(element, complete, pendingMark) {
        element.textContent = complete ? "✓" : pendingMark;
        element.classList.toggle("step-mark--complete", Boolean(complete));
    }

    function formatFileSize(size) {
        return `${(size / 1024 / 1024).toFixed(size >= 1024 * 1024 ? 1 : 2)} MB`;
    }

    async function readErrorMessage(response) {
        try {
            const body = await response.json();
            if (body && typeof body.message === "string" && body.message.trim()) {
                return body.message;
            }
        } catch (error) {
            // 非 JSON 错误响应使用下方通用提示。
        }
        return "服务处理失败，请查看运行窗口或日志。";
    }

    function extractDownloadName(contentDisposition) {
        if (!contentDisposition) {
            return "";
        }
        const encoded = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
        if (encoded) {
            try {
                return decodeURIComponent(encoded[1]);
            } catch (error) {
                return "";
            }
        }
        const plain = contentDisposition.match(/filename="?([^";]+)"?/i);
        return plain ? plain[1] : "";
    }

    function fallbackDownloadName(csvFileName) {
        const suffix = ".csv";
        const baseName = csvFileName.toLowerCase().endsWith(suffix)
            ? csvFileName.slice(0, -suffix.length)
            : csvFileName;
        return `${baseName}-解析结果.xlsx`;
    }

    function triggerDownload(blob, fileName) {
        const link = document.createElement("a");
        const url = URL.createObjectURL(blob);
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    }

    function showFeedback(type, message) {
        elements.feedback.hidden = false;
        elements.feedback.className = `feedback feedback--${type}`;
        elements.feedback.textContent = message;
    }

    function clearFeedback() {
        elements.feedback.hidden = true;
        elements.feedback.className = "feedback";
        elements.feedback.textContent = "";
    }

    initialize();
})();
