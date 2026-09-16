// Every window.<name> below is a Java handler published with WebviewBackend#bind.
// Each returns a promise that settles when Java answers.

const byId = (id) => document.getElementById(id);

async function showSystemInfo() {
    const info = JSON.parse(await window.systemInfo());
    byId("java").textContent = info.java;
    byId("os").textContent = info.os;
    byId("backend").textContent = info.backend;
    byId("platform").textContent = info.platformName;
    // A relative URL, resolved against app://local/app/ - and served from the jar like this page.
    byId("platform-image").src = `images/${info.platform}.svg`;
}

async function hashInput() {
    const output = byId("hash-output");
    output.textContent = "hashing…";
    try {
        output.textContent = await window.sha256(byId("hash-input").value);
    } catch (error) {
        output.textContent = `failed: ${error.message}`;
    }
}

// The other direction: Java evaluates a call to this function on its own schedule.
window.onHeapUsage = (megabytes) => {
    byId("heap").textContent = megabytes;
};

byId("hash-button").addEventListener("click", hashInput);
byId("hash-input").addEventListener("keydown", (event) => {
    if (event.key === "Enter") hashInput();
});

showSystemInfo();
hashInput();
