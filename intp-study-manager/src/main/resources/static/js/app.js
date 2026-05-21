document.addEventListener("click", (event) => {
    const button = event.target.closest("[data-copy-target]");
    if (!button) {
        return;
    }
    const target = document.querySelector(button.dataset.copyTarget);
    if (!target) {
        return;
    }
    navigator.clipboard.writeText(target.textContent || target.value || "").then(() => {
        button.textContent = "已复制";
        window.setTimeout(() => {
            button.textContent = "复制";
        }, 1200);
    });
});
