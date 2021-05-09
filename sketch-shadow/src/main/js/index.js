class DrawingTask {
    static CANVAS_MIN_WIDTH = 10;
    static CANVAS_MIN_HEIGHT = 10;
    static CANVAS_MAX_WIDTH = 500;
    static CANVAS_MAX_HEIGHT = 500;
    static OFFSET_FOR_TRANSPARENT = -9999;
    static toColorText(number) {
        const alpha = number >> 24 & 0xff;
        const red = number >> 16 & 0xff;
        const green = number >> 8 & 0xff;
        const blue = number & 0xff;
        return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
    }

    static BOX_RESIZE_TYPE = {
        None:0,
        Right:1,
        Bottom:2,
        Corner:3
    };
    isTransparentFill = true;
    boundPos = {
        leftPos: -1,
        topPos: -1,
        rightPos: -1,
        bottomPos: -1,
        canvasWidth: -1,
        canvasHeight: -1,
        clipLeft: -1
    };
    boxResizeMode = DrawingTask.BOX_RESIZE_TYPE.None;

    constructor(input, id) {
        const json = JSON.parse(input);
        for (let key of Object.keys(json)) {
            if (key.startsWith("round")) {
                input[key] = Math.max(0, input[key])
            } else if (key.startsWith("padding")) {
                input[key] = Math.max(-1, input[key])
            } else if (key === "outlineWidth") {
                input[key] = Math.max(0, input[key])
            }
        }
        const {
            roundLeftTop,
            roundRightTop,
            roundLeftBottom,
            roundRightBottom
        } = json;
        this.canvas = document.createElement('canvas');
        this.ctx = canvas.getContext("2d");
        this.input = json;
        this.id = id;
        this.objectWidth = 1 + Math.max(roundLeftTop + roundRightTop, roundLeftBottom + roundRightBottom);
        this.objectHeight = 1 + Math.max(roundLeftTop + roundLeftBottom, roundRightTop + roundRightBottom);
    }

    getRelativeX() {
        return Math.round((DrawingTask.CANVAS_MAX_WIDTH / 2) - (this.objectWidth / 2) - this.boundPos.leftPos);
    }

    getRelativeY() {
        return Math.round((DrawingTask.CANVAS_MAX_HEIGHT / 2) - (this.objectHeight / 2) - this.boundPos.topPos);
    }

    execute() {

        this.sendResponse().then(() => {
            console.log("complete: task id=" + this.id);
        });
    }

    drawShadow() {
        const w = this.objectWidth;
        const h = this.objectHeight;

    }

    drawShadowInternal(center, translate) {
        const {
            shadowBlur,
            shadowColor,
            shadowDx,
            shadowDy,
            fillColor,
            outlineColor,
            outlineWidth
        } = this.input;
        const w = this.objectWidth;
        const h = this.objectHeight;
        const centerPosX = Math.round((canvas.width / 2) - (w / 2));
        const centerPosY = Math.round((canvas.height / 2) - (h / 2));
        let x = 0, y = 0;
        this.ctx.save();
        if (this.isTransparentFill) {
            this.ctx.translate(DrawingTask.OFFSET_FOR_TRANSPARENT, DrawingTask.OFFSET_FOR_TRANSPARENT);
        }
        if (center) {
            x = centerPosX;
            y = centerPosY;
        } else {
            x = this.getRelativeX();
            y = this.getRelativeY();
        }
        if (this.boxResizeMode !== DrawingTask.BOX_RESIZE_TYPE.None) {
            x -= shadowDx;
            y -= shadowDy;
        }
        this.roundRect(x, y);
        if (!this.isTransparentFill) {
            this.ctx.shadowOffsetX = shadowDx;
            this.ctx.shadowOffsetY = shadowDy;
            this.ctx.shadowBlur = shadowBlur;
            this.ctx.shadowColor = DrawingTask.toColorText(shadowColor);
        } else {
            this.ctx.shadowOffsetX = shadowDx - DrawingTask.OFFSET_FOR_TRANSPARENT;
            this.ctx.shadowOffsetY = shadowDy - DrawingTask.OFFSET_FOR_TRANSPARENT;
            this.ctx.shadowBlur = shadowBlur;
            this.ctx.shadowColor = DrawingTask.toColorText(shadowColor);
        }
        this.ctx.fill();
        if (!this.isTransparentFill && outlineWidth > 0) {
            this.ctx.shadowOffsetX = 0;
            this.ctx.shadowOffsetY = 0;
            this.ctx.shadowBlur = 0;
            this.ctx.shadowColor = DrawingTask.toColorText(0);
            this.ctx.strokeStyle = outlineColor;
            this.ctx.lineWidth = outlineWidth;
            this.ctx.stroke();
        }
        this.ctx.restore();

        this.ctx.save();
        this.ctx.globalCompositeOperation = 'destination-out';
        if (center) {
            x = centerPosX;
            y = centerPosY;
        } else if (translate) {
            x = this.getRelativeX();
            y = this.getRelativeY();
        }
        if (this.boxResizeMode !== DrawingTask.BOX_RESIZE_TYPE.None) {
            x -= shadowDx;
            y -= shadowDy;
        }
        this.roundRect(x, y);
        this.ctx.fill();
        this.ctx.restore();

        if (!this.isTransparentFill) {
            this.ctx.save();
            this.ctx.fillStyle = fillColor;
            this.roundRect(x, y);
            this.ctx.fill();
            this.ctx.restore();
        }
    }

    updateBounds() {
        const w = this.objectWidth;
        const h = this.objectHeight;
    }

    preDraw() {
        this.canvas.width = CANVAS_MAX_WIDTH;
        this.canvas.height = CANVAS_MAX_HEIGHT;
        const isTransparentFillTemp = this.isTransparentFill;
        this.isTransparentFill = false;
        this.drawShadowInternal(true);
        this.updateBounds();
        this.isTransparentFill = isTransparentFillTemp;
        this.ctx.clearRect(0, 0, canvas.width, canvas.height);
    }

    async sendResponse() {
        const blob = await new Promise(resolve => {
            // noinspection JSUnresolvedVariable
            if (canvas.toBlobHD) {
                canvas.toBlobHD(resolve)
            } else {
                canvas.toBlob(resolve)
            }
        });
        const base64 = await new Promise(resolve => {
            let reader = new FileReader();
            reader.onloadend = () => {
                resolve(reader.result)
            };
            // noinspection JSCheckFunctionSignatures
            reader.readAsDataURL(blob);
        });
        // noinspection JSUnresolvedVariable
        if (typeof __handler__ !== "undefined") {
            // noinspection JSUnresolvedFunction,JSUnresolvedVariable
            __handler__.onResponse(JSON.stringify({
                margin: [],
                imageData: base64
            }, id));
        }
    }

    roundRect(x, y) {
        const {
            roundLeftTop,
            roundRightTop,
            roundLeftBottom,
            roundRightBottom
        } = this.input;
        this.ctx.beginPath();
        this.ctx.moveTo(x + roundLeftTop, y);
        this.ctx.lineTo(x + this.objectWidth - roundRightTop, y);
        this.ctx.quadraticCurveTo(x + this.objectWidth, y, x + this.objectWidth, y + roundRightTop);
        this.ctx.lineTo(x + this.objectWidth, y + this.objectHeight - roundRightBottom);
        this.ctx.quadraticCurveTo(x + this.objectWidth, y + this.objectHeight, x + this.objectWidth - roundRightBottom, y + this.objectHeight);
        this.ctx.lineTo(x + roundLeftBottom, y + this.objectHeight);
        this.ctx.quadraticCurveTo(x, y + this.objectHeight, x, y + this.objectHeight - roundLeftBottom);
        this.ctx.lineTo(x, y + roundLeftTop);
        this.ctx.quadraticCurveTo(x, y, x + roundLeftTop, y);
        this.ctx.closePath();
    }
}

async function createNinePatch(input, id) {
    new DrawingTask(input, id).execute();
}
