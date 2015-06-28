var module = angular.module("chat.messageProcessing", ["chat.services.settings", "chat.services.notifications"]);

module.service("messageProcessingService", ["$translate", "$modal", "$timeout", "chatSettings", "notificationService", function($translate, $modal, $timeout, settings, notificationService) {
    var Message = function (type, body, user, showModButtons, id_, time, showTS, hidden) {
        this.type = type;
        this.body = body;
        this.showModButtons = showModButtons;
        this.id_ = id_;
        this.user = user;
        this.hidden = hidden;
        this.likes = [];
        this.time = time;
        this.showTS = showTS;
        this.messageUpdatedCallbacks = [];
        this.addToInputCallback = angular.noop;
    };

    var MessageGroup = function(user, showModButtons, showTS) {
        this.type = "MSG_GROUP";
        this.user = user;
        this.showModButtons = showModButtons;
        this.messages = [];
        this.messageUpdatedCallbacks = [];
        this.addToInputCallback = angular.noop;
        this.showTS = showTS;
    };

    var GroupedMessage = function(body, id_, time, hidden) {
        this.body = body;
        this.id_ = id_;
        this.time = time;
        this.hidden = hidden;
        this.likes = [];
    };

    var processClearMessage = function (chat, ctx, msg) {
        chat.lastChatter(ctx.room, null);
        if (chat.self.hasGlobalRole(globalLevels.MOD) || chat.hasLocalRole(levels.MOD, ctx.room)) {
            chat.addMessage(
                new Message(msg.type, $translate.instant("CHAT_CLEAR_USER", {"mod": msg.mod, "user": msg.name}))
            );
        }
        chat.messagesUpdated();
        chat.hideMessagesFromUser(ctx.room, msg.name);
    };

    var processClearRoomMessage = function(chat, ctx) {
        chat.lastChatter(ctx.room, null);
        if (!(chat.self.hasGlobalRole(globalLevels.MOD) || chat.hasLocalRole(levels.MOD, ctx.room))) {
            chat.messages[ctx.room].length = 0;
        }
        chat.messages[ctx.room].push(new Message("INFO", $translate.instant("CHAT_CLEAR")));
        chat.messagesUpdated();
    };

    var processTextMessage = function(chat, ctx, msg) {
        ctx.proc = {
            unprocessedText: msg.text,
            mention: false
        };
        var previousMessage = chat.lastMessage[ctx.room];
        var service = msg.type === "MSG_EXT" ? ctx.msg["service"] : null;
        var serviceRes = service ? ctx.msg["serviceResource"] : null;
        var lastChatter = chat.lastChatterInRoom[ctx.room];
        var user = new User(msg.name, msg.color, levels[msg.role], globalLevels[msg.globalRole], service, serviceRes);
        ctx.proc.user = user;

        //TODO: improve
        var showModButtons = (user.role !== levels.ADMIN)
            && (chat.self && (chat.self.name !== user.name) && (msg.type != "ME")) &&
            (((chat.localRole[room] >= levels.MOD) && (chat.localRole[room] > user.role) && (chat.self.role >= user.globalRole))
            || ((chat.self.role >= globalLevels.MOD) && (chat.self.role > user.globalRole)));
        var ignored = settings.getIgnored().indexOf(user.name.toLowerCase()) != -1;
        var showIgnored = settings.getS("showIgnored");
        var hideExt = settings.getS("hideExt");
        var hidden = ignored && showIgnored;
        var omit = (ignored && !showIgnored) ||
            ((msg.type === "MSG_EXT") && ((service === "sc2tv.ru") || (service === "cybergame.tv")) && hideExt);
        var stackWithPrevious =
            (previousMessage.type === "MSG_GROUP") && ((msg.type === "MSG") || (msg.type === "MSG_EXT")) &&
            (lastChatter &&
                (lastChatter.name.toLowerCase() === user.name.toLowerCase()) &&
                (lastChatter.service === user.service)
            ) &&
            (previousMessage.messages.length < 5);

        //BODY
        processMessageText(chat, ctx, msg);

        if (!omit) {
            chat.lastChatter(ctx.room, user);
            var elem = null;
            if (stackWithPrevious) {
                previousMessage.messages.push(new GroupedMessage(ctx.proc.text, msg.id, msg.time, hidden));
                chat.messageUpdated(previousMessage);
            } else {
                if (msg.type === "MSG" || msg.type === "MSG_EXT") {
                    elem = new MessageGroup(user, showModButtons, ctx.history);
                    elem.messages.push(new GroupedMessage(ctx.proc.text, msg.id, msg.time, hidden));
                } else {
                    elem = new Message(msg.type, ctx.proc.text, user, showModButtons, msg.id, msg.time, ctx.history, hidden)
                }
            }
            if (elem != null) {
                chat.addMessage(elem, ctx.room, ctx.history, ctx.proc.mention);
                if (!ctx.history) {
                    chat.messagesUpdated();
                }
            }
            if (ctx.proc.mention && !ctx.history) {
                notificationService.notify(user.name, ctx.proc.unprocessedText);
            }
        }
    };

    var processColorMessage = function(chat, ctx, msg) {
        chat.lastChatter(ctx.room, null);
        chat.addMessage(
            new Message(
                msg.type,
                "<span style=\"opacity: 0.7\">Your color is now </span> <span style=\"color:" + msg.color + "\">" +
                    msg.color + "</span>"
            ),
            ctx.room
        );
        chat.messagesUpdated();
        chat.self.color = msg.color;
    };

    var processSelfJoinMessage = function(chat, ctx, msg) {
        chat.lastChatter(ctx.room, null);
        chat.localRole[ctx.room] = levels[ctx.msg["chatter"]["role"]];
        if (chat.self.role === globalLevels.UNAUTHENTICATED) {
            chat.addMessage(
                new Message(msg.type, $translate.instant("CHAT_HELLO_UNAUTHENTICATED", {"room": ctx.room})), ctx.room);
        } else {
            chat.addMessage(new Message(msg.type, $translate.instant("CHAT_HELLO",
                {
                    "color": chat.self.color,
                    "name": chat.self.name,
                    "role": $translate.instant("ROLE_" + ctx.msg["chatter"]["role"]),
                    "globalRole": $translate.instant("ROLE_" + chat.self.role.title),
                    "room": ctx.room
                }
            )), ctx.room);
        }
        chat.addRoom(ctx.room);
        chat.messagesUpdated();
    };

    var processTimeoutMessage = function(chat, ctx, msg) {
        chat.lastChatter(ctx.room, null);
        chat.addMessage(
            new Message(msg.type, $translate.instant("CHAT_TIMEOUT_USER", {"mod": msg.mod, "user": msg.name})), room);
        chat.hideMessagesFromUser(ctx.room, msg.name);
        if (!ctx.history) {
            chat.messagesUpdated();
        }
    };

    var processBanMessage = function(chat, ctx, msg) {
        chat.lastChatter(ctx.room, null);
        chat.addMessage(
            new Message(msg.type, $translate.instant("CHAT_BAN_USER", {"mod": msg.mod, "user": msg.name})), ctx.room);
        chat.hideMessagesFromUser(ctx.room, msg.name);
        if (!ctx.history) {
            chat.messagesUpdated();
        }
    };

    var processRoleMessage = function(chat, ctx, msg) {
        chat.lastChatter(ctx.room, null);
        if (msg.role) {
            chat.self.role = globalLevels[msg.role];
            chat.addMessage(
                new Message(msg.type, '<span style="opacity: 0.7">You are <strong>' + msg.role + '</strong></span>'),
                ctx.room
            );
        }
        chat.messagesUpdated();
    };

    var processLikeMessage = function(chat, ctx, msg) {
        var likedMessage = null;
        var group = null;
        angular.forEach(chat.messages[ctx.room], function(m) {
            if (m.type === "MSG_GROUP") {
                angular.forEach(m.messages, function(mm) {
                    if (mm.id_ === msg.id) {
                        likedMessage = mm;
                        group = m;
                    }
                });
            } else {
                if (m.id_ === msg.id) {
                    likedMessage = m;
                }
            }
        });
        if (likedMessage && likedMessage.likes.indexOf(msg.name) == -1) {
            likedMessage.likes.push(msg.name);
            if (group) {
                chat.messageUpdated(group);
            } else {
                chat.messageUpdated(likedMessage);
            }
        }
    };

    var MessageProcessingService = function() {

    };

    MessageProcessingService.prototype.processMessage = function(chat, m, hist) {
        var self = this;
        var ctx = {
            msg: m,
            room: m["room"] || chat.activeRoom,
            history: hist || false
        };
        var message = {
            type: m["type"],
            user: m["user"],
            text: m["text"],
            name: m["name"],
            mod: m["mod"],
            color: m["color"],
            role: m["role"],
            globalRole: m["globalRole"],
            id: m["messageId"],
            time: m["time"],
            pollData: m["pollData"]
        };
        ctx.currentPoll = chat.polls[ctx.room];

        switch (message.type) {
            case "CLEAR":
            case "CLEAR_EXT":
                processClearMessage(chat, ctx, message);
                break;
            case "CLEAR_ROOM":
                processClearRoomMessage(chat, ctx);
                break;
            case "HIST":
                angular.forEach(m["history"], function(e) {
                    self.processMessage(chat, e, true);
                });
                chat.messagesUpdated();
                break;
            case "ME":
            case "MSG":
            case "MSG_EXT":
            {
                processTextMessage(chat, ctx, message);
                break;
            }
            case 'COLOR':
            {
                processColorMessage(chat, ctx, message);
                break;
            }
            case 'INFO':
                chat.lastChatter(ctx.room, null);
                chat.addMessage(new Message(message.type, message.text), ctx.room);
                chat.messagesUpdated();
                break;
            case 'AUTH_REQUIRED':
                chat.addToInputCallback(chat.lastSent);
                $modal.open({
                    templateUrl: 'authentication.html',
                    controller: AuthenticationController,
                    resolve: {
                        "action": function() { return "sign_in"; },
                        "chat": function () { return chat; }
                    }
                });
                break;
            case 'RECAPTCHA':
                $modal.open({
                    templateUrl: 'anonCaptcha.html',
                    controller: AnonCaptchaController,
                    resolve: {
                        _id: function () {
                            return msg.text;
                        },
                        isUser: chat.self.role === levels.USER
                    }
                });
                break;
            case 'SELF_JOIN':
                processSelfJoinMessage(chat, ctx, message);
                break;
            case 'AUTH_COMPLETE':
                chat.self = message.user;
                chat.self.role = globalLevels[chat.self.role];
                chat.state = CHAT_STATE.AUTHENTICATED;
                chat.stateUpdatedCallback();
                break;
            case 'TIMEOUT':
                processTimeoutMessage(chat, ctx, message);
                break;
            case 'BAN':
                processBanMessage(chat, ctx, message);
                break;
            case 'LOGIN':
                chat.lastChatter(ctx.room, null);
                chat.self = message.user;
                chat.self.role = globalLevels[chat.self.role];
                chat.addMessage(new Message(message.type, $translate.instant("CHAT_LOGIN_SUCCESS",
                    {
                        "name": chat.self.name,
                        "role": $translate.instant("ROLE_" + chat.self.role.title)
                    }
                )), room);
                chat.messagesUpdated();
                break;
            case 'ROLE':
                processRoleMessage(chat, ctx, message);
                break;
            case 'ERROR':
                chat.lastChatter(ctx.room, null);
                chat.addMessage(new Message(message.type, $translate.instant("ERROR_"+text,
                    {
                        "args": m["errorData"]
                    }
                )), ctx.room);
                chat.messagesUpdated();
                break;
            case "LIKE":
                processLikeMessage(chat, ctx, message);
                break;
            case "POLL":
                chat.polls[ctx.room] = message.pollData;
                message.pollData.voted = false;
                message.pollData.open = true;
                message.pollData.maxPollVotes = Math.max.apply(null, message.pollData.votes);
                chat.pollsUpdatedCallback();
                break;
            case "POLL_UPDATE":
                if (currentPoll.poll.id === message.pollData.poll.id) {
                    currentPoll.votes = message.pollData.votes;
                    currentPoll.maxPollVotes = Math.max.apply(null, message.pollData.votes);
                    chat.pollsUpdatedCallback();
                }
                break;
            case "POLL_VOTED":
                currentPoll.voted = true;
                chat.pollsUpdatedCallback();
                break;
            case "POLL_END":
                if (currentPoll.poll.id === message.pollData.poll.id) {
                    currentPoll.votes = message.pollData.votes;
                    currentPoll.maxPollVotes = Math.max.apply(null, message.pollData.votes);
                    currentPoll.open = false;
                    chat.pollsUpdatedCallback();
                    $timeout(function() {
                        var poll = chat.polls[ctx.room];
                        if (poll.poll.id === message.pollData.poll.id) {
                            chat.polls[ctx.room] = null;
                        }
                    }, 60*1000);
                }
                break;
            default:
                console.log(message);
                break;
        }
    };

    var fetchYoutubeTitle = function(videoId, ytKey) {
        var result = null;
        $.ajax({
            "url": "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=" + videoId + "&key=" + ytKey,
            "success": function (data) {
                if (data.items && data.items[0] && data.items[0].snippet) {
                    result = "<span style=\"color:#cd201f;\" class=\"fa fa-youtube-play\"></span> " + htmlEscape(data.items[0].snippet.title);
                }
            },
            "async": false,
            "timeout": 100
        });
        return result;
    };

    var processLink = function (completeLink, prefix, link) {
        var linkText = "";
        try {
            linkText = $.trim(decodeURIComponent(link));
            if (linkText.length === 0) {
                linkText = link;
            }
        } catch (e) {
            linkText = link;
        }
        if (linkText.length > 37) {
            linkText = linkText.substr(0, 32) + "[...]";
        }
        linkText = htmlEscape(linkText);
        var notProcessed = true;
        var parsedUrl = new Uri(link);
        var host = parsedUrl.host();
        //TODO: async
        if (host === "youtube.com" || host === "www.youtube.com") {
            var videoId = null;
            if (parsedUrl.getQueryParamValue("v")) {
                videoId = parsedUrl.getQueryParamValue("v");
            }
            if (parsedUrl.getQueryParamValue("watch")) {
                videoId = parsedUrl.getQueryParamValue("watch");
            }
            if (videoId) {
                var ytTitle = fetchYoutubeTitle(videoId, ytKey);
                if (ytTitle) {
                    linkText = ytTitle;
                    notProcessed = false;
                }
            }
        }
        //TODO: async
        if (host === "youtu.be") {
            var videoId = parsedUrl.uriParts.path;
            if (videoId[0] === "/") {
                videoId = videoId.substr(1);
            }
            if (videoId) {
                var ytTitle = fetchYoutubeTitle(videoId, ytKey);
                if (ytTitle) {
                    linkText = ytTitle;
                    notProcessed = false;
                }
            }
        }
        //TODO: async
        if (notProcessed) {
            var r = /http:\/\/store\.steampowered\.com\/app\/([0-9]+)\/.*/.exec(completeLink);
            if (r && r[1]) {
                var id = r[1];
                $.ajax({
                    "url": "resolve_steam",
                    "data": {"appid": id},
                    "success": function (data) {
                        if (data) {
                            linkText = "<span style=\"color: #156291;\" class=\"fa fa-steam-square\"></span> " + htmlEscape(data);
                            notProcessed = false;
                        }
                    },
                    "async": false,
                    "timeout": 100
                });
            }
        }
        return "<a href=\"" + prefix + htmlEscape(link) + "\" target=\"_blank\" title=\"" + htmlEscape(link) + "\">" + linkText + "</a>";
    };

    var processTextPart = function(chat, ctx, msg, text) {
        text = htmlEscape(text);
        text = twemoji.parse(text, {
            base: "/img/",
            folder: "twemoji",
            ext: ".png",
            callback: function(icon, options, variant) {
                switch ( icon ) {
                    case 'a9':      // � copyright
                    case 'ae':      // � registered trademark
                    case '2122':    // � trademark
                        return false;
                }
                return ''.concat(options.base, options.size, '/', icon, options.ext);
            }
        });
        if ((msg.type === "MSG_EXT") && (ctx.proc.user.service === "sc2tv.ru")) {
            text = text.replace(/\[\/?b\]/g, "**");
            text = text.replace(SC2TV_REGEX, function (match) {
                var emoticon = SC2TV_EMOTE_MAP[match];
                if (emoticon) {
                    //TODO: better code
                    return "<span class='faceCode' style='background-image: url(/img/sc2tv/" + emoticon.fileName +
                        "); height: " + emoticon.height + "px; width: " + emoticon.width + "px;' title='" + emoticon.code + "'></span>"
                } else {
                    return null;
                }
            });
        } else {
            text = text.replace(chat.emoticonRegExp, function (match) {
                var emoticon = chat.emoticons[match];
                if (emoticon) {
                    return "<img class='emoticon' " +
                        "src='emoticons/" + emoticon.fileName + "' " +
                        "style='height: " + emoticon.height + "px; width: " + emoticon.width + "px;' " +
                        "title='" + emoticon.code + "'" +
                        "alt='" + emoticon.code + "'></img>"
                } else {
                    return null;
                }
            });
        }
        text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');
        text = text.replace(/%%(.+?)%%/g, '<span class="spoiler">$1</span>');
        text = text.replace("@" + chat.self.name, function () {
            ctx.proc.mention = true;
            return "<span class='mentionLabel'>@" + chat.self.name + "</span>"
        });
        return text;
    };

    var processMessageText = function(chat, ctx, msg) {
        ctx.proc.text = ctx.proc.unprocessedText;
        if ((msg.type === "MSG_EXT") && (ctx.proc.user.service === "sc2tv.ru")) {
            ctx.proc.text = ctx.proc.text.replace(/\[\/?url\]/g, "");
        }
        var match;
        var raw = ctx.proc.text;
        var html = [];
        var i;
        while ((match = raw.match(/(https?:\/\/)([^\s]*)/))) {
            i = match.index;
            html.push(processTextPart(chat, ctx, msg, raw.substr(0, i)));
            html.push(processLink(match[0], match[1], match[2]));
            raw = raw.substring(i + match[0].length);
        }
        html.push(processTextPart(chat, ctx, msg, raw));
        var text = html.join('');
        if (text.startsWith("&gt;")) {
            text = "<span class=\"greenText\">" + text + "</span>";
        } else if (text.indexOf("!!!") === 0 && text.length > 3) {
            text = "<span class=\"nsfwLabel\">NSFW</span> <span class=\"spoiler\">" + text.substr(3) + "</span>";
        }
        ctx.proc.text = text;
    };

    return new MessageProcessingService();
}]);