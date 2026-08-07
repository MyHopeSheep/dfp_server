(() => {
    const MAX_FILE_SIZE = 20 * 1024 * 1024;
    const SELECTED_CONFIG_KEY = "dfp.selectedConfigKey";
    const FIELD_CODES_KEY = "dfp.showFieldCodes";
    const XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    const state = {
        serviceReady: false,
        configReady: false,
        remoteProfiles: [],
        parserSettings: null,
        selectedKey: "",
        selectedFile: null,
        activeTab: "parse",
        selectedProtocolRuleKey: "",
        activeProtocolView: "timing",
        expandedTimingSteps: new Set(),
        showFieldCodes: localStorage.getItem(FIELD_CODES_KEY) === "true",
        refreshing: false,
        parsing: false
    };

    const elements = {
        serviceStatus: document.getElementById("service-status"),
        configSelect: document.getElementById("config-key"),
        configSummary: document.getElementById("config-summary"),
        configMark: document.getElementById("config-step-mark"),
        selectedProfile: document.getElementById("selected-profile"),
        fileInput: document.getElementById("csv-file"),
        fileSummary: document.getElementById("file-summary"),
        fileMark: document.getElementById("file-step-mark"),
        parseSummary: document.getElementById("parse-summary"),
        parseButton: document.getElementById("parse-button"),
        feedback: document.getElementById("feedback"),
        configToolbar: document.getElementById("config-toolbar"),
        fieldCodes: document.getElementById("show-field-codes"),
        remoteSettings: document.getElementById("remote-settings"),
        protocolSettings: document.getElementById("protocol-settings")
    };

    function initialize() {
        elements.configSelect.addEventListener("change", onConfigChanged);
        elements.fileInput.addEventListener("change", onFileChanged);
        elements.parseButton.addEventListener("click", parseAndDownload);
        elements.fieldCodes.addEventListener("change", onFieldCodesChanged);
        document.querySelectorAll("[data-refresh-configs]")
                .forEach(button => button.addEventListener("click", refreshConfigurations));
        document.querySelectorAll("[data-tab]")
                .forEach(button => button.addEventListener("click", () => switchTab(button.dataset.tab)));
        document.querySelector("[data-open-config]")
                .addEventListener("click", () => switchTab("remote"));
        elements.fieldCodes.checked = state.showFieldCodes;
        refreshHealth();
        refreshConfigurations();
        render();
    }

    async function refreshHealth() {
        elements.serviceStatus.textContent = "正在检查服务状态…";
        elements.serviceStatus.className = "service-status service-status--checking";
        try {
            const health = await fetchJson("/dfp/health");
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

    async function refreshConfigurations() {
        const previousKey = state.selectedKey;
        state.refreshing = true;
        state.configReady = false;
        render();
        try {
            const [remoteProfiles, parserSettings] = await Promise.all([
                fetchJson("/dfp/config/remoteProfiles"),
                fetchJson("/dfp/config/parserSettings")
            ]);
            if (!Array.isArray(remoteProfiles) || remoteProfiles.length === 0) {
                throw new Error("未读取到可用的遥控器配置");
            }
            if (!parserSettings || !Array.isArray(parserSettings.remoteConfigs)
                    || !Array.isArray(parserSettings.protocolRuleSets)) {
                throw new Error("协议配置响应格式无效");
            }

            const savedKey = localStorage.getItem(SELECTED_CONFIG_KEY) || "";
            const retainedKey = selectExistingKey(previousKey, savedKey, remoteProfiles);
            state.remoteProfiles = remoteProfiles;
            state.parserSettings = parserSettings;
            state.selectedKey = retainedKey;
            state.selectedProtocolRuleKey = selectProtocolRuleKey(
                state.selectedProtocolRuleKey, parserSettings.protocolRuleSets);
            state.configReady = true;
            if (previousKey && !retainedKey) {
                showFeedback("warning", "当前配置已不存在，请重新选择。");
            } else {
                clearFeedback();
            }
        } catch (error) {
            state.configReady = false;
            showFeedback("error", error.message || "配置文件读取失败，请修正后重试。");
        } finally {
            state.refreshing = false;
            render();
        }
    }

    async function fetchJson(url) {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(await readErrorMessage(response));
        }
        return response.json();
    }

    function selectExistingKey(previousKey, savedKey, profiles) {
        const keys = new Set(profiles.map(profile => profile.key));
        if (keys.has(previousKey)) {
            return previousKey;
        }
        return keys.has(savedKey) ? savedKey : "";
    }

    function selectProtocolRuleKey(previousKey, rules) {
        if (rules.some(rule => rule.key === previousKey)) {
            return previousKey;
        }
        return rules[0]?.key || "";
    }

    function switchTab(tab) {
        state.activeTab = ["parse", "remote", "protocol"].includes(tab) ? tab : "parse";
        render();
    }

    function onFieldCodesChanged() {
        state.showFieldCodes = elements.fieldCodes.checked;
        localStorage.setItem(FIELD_CODES_KEY, String(state.showFieldCodes));
        render();
    }

    function onConfigChanged() {
        state.selectedKey = elements.configSelect.value;
        if (state.selectedKey) {
            localStorage.setItem(SELECTED_CONFIG_KEY, state.selectedKey);
        } else {
            localStorage.removeItem(SELECTED_CONFIG_KEY);
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
            showFeedback("warning", "请确认服务、配置和 CSV 文件均已就绪。");
            return;
        }
        state.parsing = true;
        clearFeedback();
        render();
        try {
            const formData = new FormData();
            formData.append("file", state.selectedFile);
            formData.append("configKey", state.selectedKey);
            const response = await fetch("/dfp/csvParse", {method: "POST", body: formData});
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
        return state.serviceReady && state.configReady && Boolean(state.selectedKey)
            && state.selectedFile !== null && !state.parsing && !state.refreshing;
    }

    function render() {
        renderTabs();
        renderConfigOptions();
        renderParsePanel();
        renderRemoteSettings();
        renderProtocolSettings();
        document.body.classList.toggle("show-field-codes", state.showFieldCodes);
        elements.fieldCodes.checked = state.showFieldCodes;
        document.querySelectorAll("[data-refresh-configs]")
                .forEach(button => {
                    button.disabled = state.refreshing || state.parsing;
                    button.textContent = state.refreshing ? "正在刷新…" : "刷新配置";
                });
    }

    function renderTabs() {
        document.querySelectorAll("[data-tab]").forEach(button => {
            const active = button.dataset.tab === state.activeTab;
            button.classList.toggle("tab--active", active);
            button.setAttribute("aria-selected", String(active));
        });
        for (const tab of ["parse", "remote", "protocol"]) {
            document.getElementById(`${tab}-panel`).hidden = tab !== state.activeTab;
        }
        elements.configToolbar.hidden = state.activeTab === "parse";
    }

    function renderConfigOptions() {
        const options = [];
        if (state.remoteProfiles.length === 0) {
            options.push(createOption("", state.refreshing ? "正在读取配置…" : "配置读取失败"));
        } else {
            options.push(createOption("", "请选择遥控器配置"));
            state.remoteProfiles.forEach(profile => options.push(createOption(profile.key, profile.name)));
        }
        elements.configSelect.replaceChildren(...options);
        elements.configSelect.value = state.selectedKey;
        elements.configSelect.disabled = state.parsing || state.refreshing
            || !state.configReady || state.remoteProfiles.length === 0;
    }

    function renderParsePanel() {
        const profile = selectedProfile();
        renderStep(elements.configMark, profile, "①");
        renderStep(elements.fileMark, state.selectedFile, "②");
        elements.configSummary.textContent = profile
            ? `已选择：${profile.name}`
            : "请选择本次 CSV 解析使用的遥控器配置。";
        elements.fileSummary.textContent = state.selectedFile
            ? `已选择：${state.selectedFile.name}（${formatFileSize(state.selectedFile.size)}）`
            : "仅支持 CSV 文件，单个文件最大 20MB。";
        elements.parseSummary.textContent = profile && state.selectedFile
            ? `将使用：${profile.name} · ${state.selectedFile.name}`
            : "请先选择配置和 CSV 文件。";
        elements.parseButton.disabled = !canSubmit();
        elements.parseButton.textContent = state.parsing
            ? "正在解析，请勿重复提交…"
            : canSubmit() ? "开始解析并下载" : "请先选择配置和 CSV 文件";
        elements.fileInput.disabled = state.parsing;
        renderSelectedProfile(profile);
    }

    function renderSelectedProfile(profile) {
        elements.selectedProfile.hidden = !profile;
        if (!profile) {
            elements.selectedProfile.replaceChildren();
            return;
        }
        const values = [
            ["遥控器 ID", profile.remoteId],
            ["CSV 编码", profile.charset],
            ["空闲阈值", `${profile.idleThresholdMs} ms`],
            ["协议规则", profile.protocolRuleName]
        ];
        elements.selectedProfile.replaceChildren(...values.flatMap(([label, value]) => {
            const term = document.createElement("dt");
            const description = document.createElement("dd");
            term.textContent = label;
            description.textContent = displayValue(value);
            return [term, description];
        }));
    }

    function renderRemoteSettings() {
        if (!state.parserSettings) {
            elements.remoteSettings.replaceChildren(emptyState());
            return;
        }
        const sections = state.parserSettings.remoteConfigs.map(remote => {
            const profile = state.remoteProfiles.find(item => item.key === remote.key);
            const section = configSection(remote.name);
            appendField(section.body, "配置版本", "schemaVersion", state.parserSettings.schemaVersion);
            appendField(section.body, "遥控器代码", "remoteConfigs[].key", remote.key);
            appendField(section.body, "配置名称", "remoteConfigs[].name", remote.name);
            appendField(section.body, "遥控器 ID", "remoteConfigs[].remoteId", remote.remoteId);
            appendField(section.body, "CSV 字符编码", "remoteConfigs[].charset", remote.charset);
            appendField(section.body, "空闲判定阈值", "remoteConfigs[].idleThresholdMs", `${remote.idleThresholdMs} ms`);
            appendField(section.body, "空闲电平", "remoteConfigs[].idleLevel", formatIdleLevel(remote.idleLevel));
            appendField(section.body, "电平映射", "remoteConfigs[].levelMapping", formatLevelMapping(remote.levelMapping));
            appendField(section.body, "关联规则代码", "remoteConfigs[].protocolRuleKey", remote.protocolRuleKey);
            appendField(section.body, "关联规则名称", "remoteProfiles[].protocolRuleName", profile?.protocolRuleName);
            return section.root;
        });
        elements.remoteSettings.replaceChildren(...sections);
    }

    function renderProtocolSettings() {
        if (!state.parserSettings) {
            elements.protocolSettings.replaceChildren(emptyState());
            return;
        }
        const rules = state.parserSettings.protocolRuleSets;
        const rule = rules.find(item => item.key === state.selectedProtocolRuleKey) || rules[0];
        if (!rule) {
            elements.protocolSettings.replaceChildren(emptyState());
            return;
        }
        state.selectedProtocolRuleKey = rule.key;

        const content = state.activeProtocolView === "frame"
            ? buildFrameView(rule)
            : state.activeProtocolView === "commands"
                ? buildCommandView(rule)
                : buildTimingView(rule);
        elements.protocolSettings.replaceChildren(
            buildProtocolRuleBar(rule, rules),
            buildProtocolSummary(rule),
            buildProtocolSubtabs(),
            content);
    }

    function buildProtocolRuleBar(rule, rules) {
        const bar = document.createElement("section");
        bar.className = "protocol-rule-bar";

        const identity = document.createElement("div");
        identity.className = "protocol-rule-identity";
        if (rules.length === 1) {
            const heading = document.createElement("h3");
            heading.textContent = rule.name;
            const code = document.createElement("code");
            code.className = "protocol-rule-key";
            code.textContent = rule.key;
            identity.append(heading, code);
        } else {
            const label = document.createElement("label");
            label.htmlFor = "protocol-rule-select";
            label.textContent = "当前规则集";
            const select = document.createElement("select");
            select.id = "protocol-rule-select";
            rules.forEach(item => select.append(createOption(item.key, `${item.name} · ${item.key}`)));
            select.value = rule.key;
            select.addEventListener("change", () => {
                state.selectedProtocolRuleKey = select.value;
                state.expandedTimingSteps.clear();
                renderProtocolSettings();
            });
            identity.append(label, select);
        }

        bar.append(identity);
        return bar;
    }

    function buildProtocolSummary(rule) {
        const summary = document.createElement("section");
        summary.className = "protocol-summary";
        const values = [
            ["算法模式", "局部周期估计 · 帧内固定周期采样"],
            ["同步标记", rule.frame.syncMarkerHex],
            ["正式帧", `${rule.frame.formalFrameBits} bit / ${rule.derived.formalFrameBytes} 字节`]
        ];
        values.forEach(([label, value]) => {
            const item = document.createElement("div");
            const term = document.createElement("span");
            const description = document.createElement("strong");
            term.textContent = label;
            description.textContent = value;
            item.append(term, description);
            summary.append(item);
        });
        const boundary = document.createElement("p");
        boundary.textContent = "本页展示解析器使用的算法规则，不表示某次 CSV 的执行结果。单帧实际周期、拟合残差、采样相位和文件尾恢复结果请查看 Excel 帧审计。";
        summary.append(boundary);
        return summary;
    }

    function buildProtocolSubtabs() {
        const tabs = document.createElement("nav");
        tabs.className = "protocol-subtabs";
        tabs.setAttribute("aria-label", "协议规则子视图");
        tabs.setAttribute("role", "tablist");
        [
            ["timing", "时序判定"],
            ["frame", "帧结构"],
            ["commands", "命令映射"]
        ].forEach(([key, label]) => {
            const button = document.createElement("button");
            const active = state.activeProtocolView === key;
            button.type = "button";
            button.className = `protocol-subtab${active ? " protocol-subtab--active" : ""}`;
            button.textContent = label;
            button.setAttribute("role", "tab");
            button.setAttribute("aria-selected", String(active));
            button.addEventListener("click", () => {
                state.activeProtocolView = key;
                renderProtocolSettings();
            });
            tabs.append(button);
        });
        return tabs;
    }

    function buildTimingView(rule) {
        const view = document.createElement("section");
        view.className = "protocol-view timing-view";
        const header = document.createElement("div");
        header.className = "timing-view__header";
        const intro = document.createElement("p");
        intro.textContent = "解析器按时间顺序扫描整个 CSV 的真实边沿。空闲区段只用于未识别范围和文件边界残片审计，不作为“一段就是一帧”的解析边界。";
        const steps = timingSteps(rule);
        const allExpanded = state.expandedTimingSteps.size === steps.length;
        const expandButton = document.createElement("button");
        expandButton.type = "button";
        expandButton.className = "text-button";
        expandButton.dataset.timingFocus = "expand-all";
        expandButton.textContent = allExpanded ? "收起全部" : "展开全部";
        expandButton.addEventListener("click", () => {
            state.expandedTimingSteps = allExpanded
                ? new Set()
                : new Set(steps.map(step => step.number));
            rerenderTimingView("expand-all");
        });
        header.append(intro, expandButton);

        const stepper = document.createElement("div");
        stepper.className = "timing-stepper";
        steps.forEach(step => stepper.append(buildTimingStep(step)));
        view.append(header, stepper);
        return view;
    }

    function timingSteps(rule) {
        const timing = rule.timing;
        const frame = rule.frame;
        const derived = rule.derived;
        const pulseRange = `${derived.singleBitPulseMinUs}～${derived.singleBitPulseMaxUs} µs`;
        const recoverableDirectBits = frame.formalFrameBits - 1;
        const noRecoveryMaximum = frame.formalFrameBits - 2;
        return [
            {
                number: "01",
                label: "01 筛选连续 1T 候选脉冲",
                summary: "从相邻真实边沿中寻找可用于初始拟合的连续 1T 候选脉冲。",
                action: "计算相邻两条真实边沿记录的时间差，连续筛选可能属于一个 bit 周期的脉冲。2T、3T 间隔不计入初始有效脉冲数量。",
                rules: [
                    ["预筛选基准周期", "protocolRuleSets[].timing.nominalBitPeriodUs", `${timing.nominalBitPeriodUs} µs`],
                    ["预筛选容差", "protocolRuleSets[].timing.prefilterToleranceRatio", `±${percent(timing.prefilterToleranceRatio)}`],
                    ["候选脉宽范围", "protocolRuleSets[].derived.singleBitPulseMinUs / protocolRuleSets[].derived.singleBitPulseMaxUs", pulseRange, "计算值"],
                    ["候选范围公式", null, "候选下限 = nominalBitPeriodUs × (1 - prefilterToleranceRatio)；候选上限 = nominalBitPeriodUs × (1 + prefilterToleranceRatio)", "算法固定规则"],
                    ["连续脉冲数量", "protocolRuleSets[].timing.minValidPulseCount / protocolRuleSets[].timing.maxInitialFitPulseCount", `${timing.minValidPulseCount}～${timing.maxInitialFitPulseCount} 个`]
                ],
                condition: "连续候选脉冲不少于配置下限，且当前窗口每个相邻脉宽都位于候选范围内；上下限均包含。",
                failure: "不形成初始周期候选，继续从后续真实边沿扫描，不据此生成“非有效帧”。"
            },
            {
                number: "02",
                label: "02 初始局部周期拟合",
                summary: "使用连续 1T 候选边沿做线性拟合，得到初始周期和逻辑边界相位。",
                action: "对候选窗口的真实边沿时间和连续 bit 边界编号执行线性最小二乘拟合，计算 initial T_est、归一化边界相位和最大残差。",
                rules: [
                    ["最大拟合残差比", "protocolRuleSets[].timing.maxFitResidualRatio", percent(timing.maxFitResidualRatio)],
                    ["可靠性公式", null, "initialResidualRatio ≤ maxFitResidualRatio", "算法固定规则"]
                ],
                condition: "拟合周期为有限正数，边沿时间和 bit 编号严格递增，最大拟合残差比不超过阈值；等于阈值时允许通过。",
                failure: `放弃当前初始周期候选并继续扫描，不使用 ${timing.nominalBitPeriodUs} µs 或其他标称值作为固定采样周期。`
            },
            {
                number: "03",
                label: "03 第一次确认同步标记",
                summary: "基于初始周期尝试有限采样相位，确认是否能够恢复同步标记。",
                action: "在 [0, initial T_est) 内生成配置数量的采样相位，按每个相位恢复同步窗口并与当前同步标记比较。",
                rules: [
                    ["采样相位数量", "protocolRuleSets[].timing.phaseCount", `${timing.phaseCount} 个`],
                    ["采样相位步长", "protocolRuleSets[].derived.phaseStepExpression", derived.phaseStepExpression, "计算值"],
                    ["相位枚举公式", null, "phaseStep = initial T_est / phaseCount；samplePhase = k × phaseStep，k = 0 ... phaseCount - 1", "算法固定规则"],
                    ["同步标记", "protocolRuleSets[].frame.syncMarkerHex", frame.syncMarkerHex]
                ],
                condition: "至少一个相位完整恢复出同步标记。",
                failure: "放弃当前同步候选，不进入同步结构细化拟合，也不生成虚假的正式帧记录。"
            },
            {
                number: "04",
                label: "04 同步结构细化拟合",
                summary: "按同步标记的已知跳变位置重新关联真实边沿，细化周期和逻辑边界相位。",
                action: "根据同步标记的真实 bit 跳变编号，在每个预期跳变附近选择最近且时间递增的真实边沿，并使用真实 bit 编号重新拟合。",
                rules: [
                    ["同步跳变编号", "protocolRuleSets[].derived.markerTransitionIndexes", derived.markerTransitionIndexes.join(", "), "计算值"],
                    ["同步边沿关联范围", null, "±0.5 × initial T_est", "算法固定规则"],
                    ["细化残差阈值", "protocolRuleSets[].timing.maxFitResidualRatio", percent(timing.maxFitResidualRatio)]
                ],
                condition: "所有要求的同步跳变均映射成功，并且 refinedResidualRatio ≤ maxFitResidualRatio。2T、3T 保留真实 bit 编号差参与细化。",
                failure: "放弃当前同步候选，不执行正式帧采样。"
            },
            {
                number: "05",
                label: "05 第二次确认同步标记并采样正式帧",
                summary: "使用细化后的周期重新搜索相位并再次确认同步标记，确认后固定周期采样正式帧。",
                action: "使用 refined T_est 重新生成全部相位；同步窗口再次匹配后，从正式帧头第一个 bit 的估算逻辑边界开始采样。",
                rules: [
                    ["同步前导长度", "protocolRuleSets[].derived.syncPreludeBits", `${derived.syncPreludeBits} bit`, "计算值"],
                    ["正式帧长度", "protocolRuleSets[].frame.formalFrameBits", `${frame.formalFrameBits} bit`],
                    ["帧内采样模式", null, "refined T_est + samplePhase 固定使用", "算法固定规则"]
                ],
                condition: "细化残差通过，且至少一个细化后相位再次完整恢复当前同步标记。",
                failure: "细化后没有任何相位再次恢复同步标记时，放弃当前同步候选。"
            },
            {
                number: "06",
                label: "06 文件尾最后 1 bit 受限恢复",
                summary: `正式帧直接恢复不足时，只处理“第 ${frame.formalFrameBits} bit 电平已记录但结束边界缺失”的文件尾情况。`,
                action: `直接恢复 ${frame.formalFrameBits} bit 时无需恢复；恰好 ${recoverableDirectBits} bit 时检查全部文件尾证据；${noRecoveryMaximum} bit 及以下禁止补齐。`,
                rules: [
                    ["可恢复的直接 bit 数", "protocolRuleSets[].frame.formalFrameBits", `${recoverableDirectBits} → ${frame.formalFrameBits} bit`, "计算值"],
                    ["最后边沿容差", "protocolRuleSets[].timing.tailEdgeToleranceRatio", percent(timing.tailEdgeToleranceRatio)],
                    ["前一脉冲误差比例", "protocolRuleSets[].derived.previousPulseToleranceRatio", percent(derived.previousPulseToleranceRatio), "计算值"],
                    ["最多恢复 bit 数", null, "1 bit", "算法固定规则"]
                ],
                condition: "必须同时满足：1. 已完成第二次同步标记确认；2. 当前采样停在 CSV 最后一条真实边沿，后续不存在其他真实边沿；3. CSV 直接恢复恰好少 1 bit；4. 最后两条真实边沿时间严格递增且电平发生变化；5. 最后边沿与最后 bit 预期起点的偏差不超过 tailEdgeToleranceRatio × T_est；6. 前一真实脉宽仍位于候选脉宽范围；7. 前一脉宽误差不超过 previousPulseToleranceRatio × T_est；8. 最后 bit 实际采样时间不早于最后边沿，且早于正式帧估算结束时间。",
                failure: "保留直接恢复结果但不补齐；不得根据预期尾帧猜测 bit，也不得补齐两个及以上 bit。"
            },
            {
                number: "07",
                label: "07 正式帧协议规则校验",
                summary: `按 ${derived.formalFrameBytes} 字节正式帧结构检查完整性、帧头、遥控器 ID、命令码和尾帧。`,
                action: "按固定顺序执行完整性、帧头、解析任务所选遥控器 ID、已知命令集合和尾帧校验。",
                rules: [
                    ["正式帧头", "protocolRuleSets[].frame.formalHeaderHex", frame.formalHeaderHex],
                    ["遥控器 ID 来源", "remoteConfigs[].remoteId", "解析任务所选遥控器配置"],
                    ["遥控器 ID 偏移", "protocolRuleSets[].frame.remoteIdByteOffset", `${frame.remoteIdByteOffset} 字节`],
                    ["命令码偏移", "protocolRuleSets[].frame.commandByteOffset", `${frame.commandByteOffset} 字节`],
                    ["尾帧", "protocolRuleSets[].frame.tailHex", frame.tailHex]
                ],
                condition: "正式帧完整，并依次通过帧头、遥控器 ID、命令码和尾帧校验。",
                failure: "已形成的正式帧候选继续保留并标记为“非有效帧”。失败原因包括：正式帧数据截断、帧头不匹配、遥控器 ID 不匹配、命令字节未知、尾帧不匹配。"
            },
            {
                number: "08",
                label: "08 同一物理帧候选择优与去重",
                summary: "合并不同初始窗口或采样相位产生的同一物理帧候选，只保留证据更强的结果。",
                action: "以正式帧起点时间差和两个候选中较大的 T_est 计算动态去重容差，再按证据等级逐项选择。",
                rules: [
                    ["去重时间容差比", "protocolRuleSets[].timing.dedupToleranceRatio", percent(timing.dedupToleranceRatio)],
                    ["重复边界", null, "|formalStartA - formalStartB| ≤ ratio × max(T_est)", "算法固定规则"],
                    ["采样安全距离", null, "仅用于同层候选择优，无独立通过阈值", "算法固定规则"]
                ],
                condition: "优先级依次为：直接恢复且有效、补齐 1 bit 后有效、完整但无效、不完整；同层再比较初始脉冲数、细化残差、安全距离、T_est 和 samplePhase。",
                failure: "超过去重时间边界时保留为不同候选；不同发送时间的相同命令不按字节内容去重。"
            }
        ];
    }

    function buildTimingStep(step) {
        const expanded = state.expandedTimingSteps.has(step.number);
        const section = document.createElement("section");
        section.className = `timing-step${expanded ? " timing-step--expanded" : ""}`;
        const detailsId = `timing-step-details-${step.number}`;

        const button = document.createElement("button");
        button.type = "button";
        button.className = "timing-step__toggle";
        button.dataset.timingFocus = step.number;
        button.setAttribute("aria-expanded", String(expanded));
        button.setAttribute("aria-controls", detailsId);
        const number = document.createElement("span");
        number.className = "timing-step__number";
        number.textContent = step.number;
        const title = document.createElement("span");
        title.className = "timing-step__title";
        title.textContent = step.label.substring(3);
        const chevron = document.createElement("span");
        chevron.className = "timing-step__chevron";
        chevron.textContent = expanded ? "−" : "+";
        chevron.setAttribute("aria-hidden", "true");
        button.append(number, title, chevron);
        button.addEventListener("click", () => {
            if (expanded) {
                state.expandedTimingSteps.delete(step.number);
            } else {
                state.expandedTimingSteps.add(step.number);
            }
            rerenderTimingView(step.number);
        });

        const summary = document.createElement("p");
        summary.className = "timing-step__summary";
        summary.textContent = step.summary;

        const details = document.createElement("div");
        details.id = detailsId;
        details.className = "timing-step__details";
        details.hidden = !expanded;
        details.append(
            timingDetail("解析动作", step.action),
            timingRuleDetail(step.rules),
            timingDetail("进入下一步或处理条件", step.condition),
            timingDetail("不满足时", step.failure));
        section.append(button, summary, details);
        return section;
    }

    function rerenderTimingView(focusKey) {
        const scrollTop = window.scrollY;
        renderProtocolSettings();
        requestAnimationFrame(() => {
            window.scrollTo(0, scrollTop);
            const replacement = elements.protocolSettings
                .querySelector(`[data-timing-focus="${focusKey}"]`);
            replacement?.focus({preventScroll: true});
        });
    }

    function timingDetail(label, text) {
        const block = document.createElement("section");
        block.className = "timing-detail";
        const heading = document.createElement("h5");
        const content = document.createElement("p");
        heading.textContent = label;
        content.textContent = text;
        block.append(heading, content);
        return block;
    }

    function timingRuleDetail(rules) {
        const block = document.createElement("section");
        block.className = "timing-detail timing-detail--rules";
        const heading = document.createElement("h5");
        heading.textContent = "当前规则";
        const list = document.createElement("dl");
        list.className = "timing-rule-list";
        rules.forEach(([label, path, value, badge]) => {
            const row = document.createElement("div");
            const term = document.createElement("dt");
            const description = document.createElement("dd");
            const name = document.createElement("span");
            name.textContent = label;
            term.append(name);
            if (badge) {
                const badgeElement = document.createElement("span");
                badgeElement.className = `value-badge ${badge === "算法固定规则"
                    ? "value-badge--algorithm" : "value-badge--derived"}`;
                badgeElement.textContent = badge;
                term.append(badgeElement);
            }
            if (path) {
                const code = document.createElement("code");
                code.className = "field-code";
                code.textContent = path;
                term.append(code);
            }
            description.textContent = value;
            row.append(term, description);
            list.append(row);
        });
        block.append(heading, list);
        return block;
    }

    function buildFrameView(rule) {
        const view = document.createElement("section");
        view.className = "protocol-view frame-view";
        const intro = document.createElement("div");
        intro.className = "view-intro";
        const heading = document.createElement("h3");
        heading.textContent = "当前协议固定结构";
        const copy = document.createElement("p");
        copy.textContent = "正式帧从帧头第一个字节开始；用于识别的同步前导在字节带外单独展示。";
        intro.append(heading, copy);

        const stripWrap = document.createElement("div");
        stripWrap.className = "frame-byte-scroll";
        const strip = document.createElement("div");
        strip.className = "frame-byte-strip";
        strip.style.gridTemplateColumns = `repeat(${rule.derived.formalFrameBytes}, minmax(72px, 1fr))`;
        frameByteSpecs(rule).forEach(spec => {
            const cell = document.createElement("div");
            cell.className = `frame-byte frame-byte--${spec.group}`;
            const offset = document.createElement("span");
            const value = document.createElement("strong");
            const label = document.createElement("small");
            offset.textContent = `偏移 ${spec.offset}`;
            value.textContent = spec.value;
            label.textContent = spec.label;
            cell.append(offset, value, label);
            strip.append(cell);
        });
        stripWrap.append(strip);

        const metrics = document.createElement("dl");
        metrics.className = "frame-metrics";
        const markerBytes = splitHex(rule.frame.syncMarkerHex);
        const prelude = markerBytes.slice(0, rule.derived.syncPreludeBytes).join(" ");
        appendFrameMetric(metrics, "识别同步前导", prelude,
            "protocolRuleSets[].derived.syncPreludeBytes");
        appendFrameMetric(metrics, "完整识别标记", rule.frame.syncMarkerHex,
            "protocolRuleSets[].frame.syncMarkerHex");
        appendFrameMetric(metrics, "识别核心窗口",
            `${rule.derived.syncInclusiveBits} bit / ${rule.derived.syncInclusiveBytes} 字节`,
            "protocolRuleSets[].derived.syncInclusiveBits / protocolRuleSets[].derived.syncInclusiveBytes");
        appendFrameMetric(metrics, "正式帧",
            `${rule.frame.formalFrameBits} bit / ${rule.derived.formalFrameBytes} 字节`,
            "protocolRuleSets[].frame.formalFrameBits / protocolRuleSets[].derived.formalFrameBytes");

        const notes = document.createElement("ul");
        notes.className = "structure-notes";
        [
            "同步前导用于周期估计、相位定位和同步确认，不写入正式帧 bit 串和字节串。",
            "加密字段和 RF 数据当前只读取、保留和输出，不参与有效性校验。",
            "遥控器 ID、命令码和尾帧参与正式帧有效性校验。"
        ].forEach(text => {
            const item = document.createElement("li");
            item.textContent = text;
            notes.append(item);
        });
        view.append(intro, stripWrap, metrics, notes);
        return view;
    }

    function frameByteSpecs(rule) {
        const header = splitHex(rule.frame.formalHeaderHex);
        const tail = splitHex(rule.frame.tailHex);
        return Array.from({length: rule.derived.formalFrameBytes}, (_, offset) => {
            if (offset < header.length) {
                return {offset, value: header[offset], label: "帧头", group: "header"};
            }
            if (offset < rule.frame.remoteIdByteOffset) {
                return {offset, value: `ENC${offset - header.length + 1}`, label: "加密字段", group: "encrypted"};
            }
            if (offset < rule.frame.remoteIdByteOffset + 3) {
                return {offset, value: `ID${offset - rule.frame.remoteIdByteOffset + 1}`, label: "遥控器 ID", group: "remote"};
            }
            if (offset === rule.frame.commandByteOffset - 1) {
                return {offset, value: "RF_DATA", label: "RF 数据", group: "rf"};
            }
            if (offset === rule.frame.commandByteOffset) {
                return {offset, value: "CMD", label: "命令码", group: "command"};
            }
            if (offset >= rule.frame.tailByteOffset) {
                return {offset, value: tail[offset - rule.frame.tailByteOffset] || "--", label: "尾帧", group: "tail"};
            }
            return {offset, value: `BYTE${offset}`, label: "保留字段", group: "other"};
        });
    }

    function appendFrameMetric(container, label, value, path) {
        const row = document.createElement("div");
        const term = document.createElement("dt");
        const description = document.createElement("dd");
        const code = document.createElement("code");
        term.textContent = label;
        code.className = "field-code";
        code.textContent = path;
        term.append(code);
        description.textContent = value;
        row.append(term, description);
        container.append(row);
    }

    function buildCommandView(rule) {
        const view = document.createElement("section");
        view.className = "protocol-view command-view";
        const intro = document.createElement("div");
        intro.className = "view-intro";
        const heading = document.createElement("h3");
        const copy = document.createElement("p");
        heading.textContent = `全部命令映射 · ${rule.commands.length} 条`;
        copy.textContent = "命令字节不在当前规则的已知命令集合中时，该候选帧判定为非有效帧。";
        intro.append(heading, copy);

        const tableWrap = document.createElement("div");
        tableWrap.className = "table-wrap command-table-wrap";
        const table = document.createElement("table");
        const head = document.createElement("thead");
        const headerRow = document.createElement("tr");
        ["通道", "命令码", "动作"].forEach(label => {
            const cell = document.createElement("th");
            cell.scope = "col";
            cell.textContent = label;
            headerRow.append(cell);
        });
        head.append(headerRow);
        const sortedCommands = [...rule.commands]
            .sort((left, right) => left.channel.localeCompare(right.channel, "zh-CN", {numeric: true})
                || parseInt(left.code, 16) - parseInt(right.code, 16));
        const groupedCommands = sortedCommands.reduce((groups, command) => {
            const commands = groups.get(command.channel) || [];
            commands.push(command);
            groups.set(command.channel, commands);
            return groups;
        }, new Map());
        table.append(head);
        groupedCommands.forEach((commands, channel) => {
            const body = document.createElement("tbody");
            commands.forEach((command, index) => {
                const row = document.createElement("tr");
                if (index === 0) {
                    const channelCell = document.createElement("th");
                    channelCell.scope = "rowgroup";
                    channelCell.rowSpan = commands.length;
                    channelCell.textContent = displayValue(channel);
                    row.append(channelCell);
                }
                [`0x${command.code.toUpperCase().padStart(2, "0")}`, command.action]
                    .forEach(value => {
                        const cell = document.createElement("td");
                        cell.textContent = displayValue(value);
                        row.append(cell);
                    });
                body.append(row);
            });
            table.append(body);
        });
        tableWrap.append(table);
        view.append(intro, tableWrap);
        return view;
    }

    function splitHex(text) {
        return String(text || "").trim().split(/\s+/).filter(Boolean);
    }

    function configSection(title) {
        const root = document.createElement("section");
        root.className = "config-section";
        const heading = document.createElement("h4");
        heading.textContent = title;
        const body = document.createElement("dl");
        body.className = "field-list";
        root.append(heading, body);
        return {root, body};
    }

    function appendField(container, label, path, value, badge) {
        const row = document.createElement("div");
        row.className = "field-row";
        const term = document.createElement("dt");
        const labelElement = document.createElement("span");
        labelElement.textContent = label;
        term.append(labelElement);
        if (badge) {
            const badgeElement = document.createElement("span");
            badgeElement.className = `value-badge value-badge--${badge === "固定" ? "fixed" : "derived"}`;
            badgeElement.textContent = badge;
            term.append(badgeElement);
        }
        const code = document.createElement("code");
        code.className = "field-code";
        code.textContent = path;
        term.append(code);
        const description = document.createElement("dd");
        description.textContent = displayValue(value);
        row.append(term, description);
        container.append(row);
    }

    function emptyState() {
        const message = document.createElement("p");
        message.className = "empty-state";
        message.textContent = state.refreshing ? "正在读取配置…" : "配置尚未成功读取。";
        return message;
    }

    function selectedProfile() {
        return state.remoteProfiles.find(profile => profile.key === state.selectedKey);
    }

    function createOption(value, label) {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = label;
        return option;
    }

    function renderStep(element, complete, pendingMark) {
        element.textContent = complete ? "✓" : pendingMark;
        element.classList.toggle("step-mark--complete", Boolean(complete));
    }

    function formatIdleLevel(value) {
        return value === 0 ? "0（低电平）" : value === 1 ? "1（高电平）" : "—";
    }

    function formatLevelMapping(value) {
        return value === "direct" ? "直接映射" : value === "inverted" ? "反相映射" : displayValue(value);
    }

    function percent(value) {
        if (value === null || value === undefined) {
            return "—";
        }
        return `${Number((Number(value) * 100).toFixed(6))}%`;
    }

    function displayValue(value) {
        return value === null || value === undefined || value === "" ? "—" : String(value);
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
            // 非 JSON 错误响应使用通用提示。
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
            ? csvFileName.slice(0, -suffix.length) : csvFileName;
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
