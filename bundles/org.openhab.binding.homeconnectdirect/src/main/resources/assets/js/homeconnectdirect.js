const messageMap = new Map();
let currentMessageId = 0;

function createWebSocketConnection(endpoint) {
    const protocol = window.location.protocol === "https:" ? "wss://" : "ws://";
    const websocketUrl = protocol + window.location.host + endpoint;

    console.log(`Connecting to WebSocket at: ${websocketUrl}`);

    const socket = new WebSocket(websocketUrl);
    let pingInterval;

    socket.addEventListener("open", () => {
        console.log("WebSocket connection established.");
        pingInterval = setInterval(() => {
            if (socket.readyState === WebSocket.OPEN) {
                socket.send("PING");
            }
        }, 30000);
    });

    socket.addEventListener("message", (event) => {
        if (event.data === "PONG") {
            console.log("Received PONG from server.");
        } else {
            const data = JSON.parse(event.data);
            console.log("Message from server:", data);

            const messageId = currentMessageId++;
            messageMap.set(messageId, data);

            addMessageRow(messageId, data)
        }


    });

    socket.addEventListener("error", (error) => {
        console.error("WebSocket error:", error);
    });

    socket.addEventListener("close", () => {
        console.log("WebSocket connection closed.");
        clearInterval(pingInterval);
    });

    return socket;
}

function addMessageRow(id, message) {
    const row = document.createElement("tr");
    row.classList.add("fade-in");
    row.dataset.id = id;

    let badgeClass = getActionBadgeClass(message.action);

    let dataEntries = '';
    if (message.data.length > 0) {
        dataEntries = formatDataEntries(message.data);
    }

    row.innerHTML = `
                <td>${message.type === "INCOMING" ? "<span class=\"mdi mdi-arrow-down-bold text-danger\"></span>"
                                                  : "<span class=\"mdi mdi-arrow-up-bold text-success\"></span>"} ${message.dateTime}</td>
                <td><span class="badge ${badgeClass}">${message.action}</span></td>
                <td>${message.resource} (v${message.version})</td>
                <td><pre>${dataEntries}</pre></td>
            `;
    const isAtBottom = Math.abs(eventTableContainer.scrollTop + eventTableContainer.clientHeight - eventTableContainer.scrollHeight) < 10;
    eventTableBody.appendChild(row);

    if (isAtBottom) {
        //row.scrollIntoView({ behavior: "smooth", block: "end" });
        eventTableContainer.scrollTop = eventTableContainer.scrollHeight;
    }

    row.addEventListener('click', () => {
        const id = parseInt(row.getAttribute('data-id'));
        const messageObject = messageMap.get(id);

        if (messageObject) {
            let badgeClass = getActionBadgeClass(messageObject.action);

            document.getElementById('modal-timestamp').textContent = messageObject.dateTime;
            document.getElementById('modal-action').innerHTML = '<span class="badge ' + badgeClass + '">' + messageObject.action + '</span>';
            document.getElementById('modal-resource').textContent = messageObject.resource;

            let formattedData = '';
            if (messageObject.data.length > 0) {
                formattedData = JSON.stringify(messageObject.data, null, 2);
            } else if (messageObject.descriptions.length > 0) {
                formattedData = JSON.stringify(messageObject.descriptions, null, 2);
            } else {
                formattedData = JSON.stringify(messageObject.source.data, null, 2);
            }

            let dataElement = document.getElementById('modal-data');
            let dataRowElement = document.getElementById('modal-data-row');

            if (formattedData == null || formattedData.trim().length === 0) {
                dataRowElement.style.display = 'none';
                dataElement.textContent = '';
            } else {
                dataRowElement.style.display = 'block';
                //dataElement.textContent = formattedData;
                dataElement.innerHTML = syntaxHighlight(formattedData);
            }

            //document.getElementById('modal-source').textContent = JSON.stringify(messageObject.source, null, 2);
            document.getElementById('modal-source').innerHTML = syntaxHighlight(messageObject.source);

            var modalElement = document.getElementById('detailsModal');
            var modal = new bootstrap.Modal(modalElement);
            modal.show();
        }
    });
}

function adjustTableHeight() {
    const headerHeight = document.querySelector('h1').offsetHeight;
    const navbarHeight = document.querySelector('nav').offsetHeight;
    const padding = 85;
    const availableHeight = window.innerHeight - navbarHeight - headerHeight - padding;
    const eventTableContainer = document.getElementById('eventTableContainer');
    eventTableContainer.style.height = availableHeight + 'px';
}

function addResizeListener() {
    window.addEventListener('load', adjustTableHeight);
    window.addEventListener('resize', adjustTableHeight);
}

function formatDataEntries(dataArray) {
    const entriesToDisplay = dataArray.slice(0, 3);
    const formattedEntries = entriesToDisplay.map(obj => `${obj.name}: ${obj.value}`);

    let result = formattedEntries.join('\n');

    if (dataArray.length > 3) {
        result += '\n...';
    }

    return result;
}

function getActionBadgeClass(action) {
    let badgeClass = 'text-bg-secondary';
    if (action === 'NOTIFY') {
        badgeClass = 'text-bg-warning';
    } else if (action === 'GET' || action === 'POST') {
        badgeClass = 'text-bg-success';
    }
    return badgeClass;
}

function syntaxHighlight(json) {
    if (typeof json != 'string') {
        json = JSON.stringify(json, undefined, 2);
    }
    json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|\b[\d.eE+-]+\b)/g, function (match) {
        let cls = 'number';
        if (/^"/.test(match)) {
            if (/:$/.test(match)) {
                cls = 'key';
            } else {
                cls = 'string';
            }
        } else if (/true|false/.test(match)) {
            cls = 'boolean';
        } else if (/null/.test(match)) {
            cls = 'null';
        }
        return '<span class="' + cls + '">' + match + '</span>';
    });
}
