function toColorText(number) {
    const alpha = number >> 24 & 0xff;
    const red = number >> 16 & 0xff;
    const green = number >> 8 & 0xff;
    const blue = number & 0xff;
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

const CANVAS_MAX_WIDTH = 500;
const CANVAS_MAX_HEIGHT = 500;
const OFFSET_FOR_TRANSPARENT = -9999;
let NINE_PATCH_SIZING_WIDTH = 4;

const BoxResizeType = {
    None: 0,
    Right: 1,
    Bottom: 2,
    Corner: 3
};

class DrawingTask {
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
    boxResizeMode = BoxResizeType.None;

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
        this.ctx = this.canvas.getContext("2d");
        this.input = json;
        this.id = id;
        this.objectWidth = 1 + Math.max(roundLeftTop + roundRightTop, roundLeftBottom + roundRightBottom);
        this.objectHeight = 1 + Math.max(roundLeftTop + roundLeftBottom, roundRightTop + roundRightBottom);
    }

    draw() {
        this.drawShadow()
    }

    getRelativeX() {
        return Math.round((CANVAS_MAX_WIDTH / 2) - (this.objectWidth / 2) - this.boundPos.leftPos);
    }

    getRelativeY() {
        return Math.round((CANVAS_MAX_HEIGHT / 2) - (this.objectHeight / 2) - this.boundPos.topPos);
    }

    execute() {
        this.draw();
        this.sendResponse().then(() => console.log("complete: task id=" + this.id));
    }

    drawShadow() {
        this.preDraw();
        //Set canvas size to calculated size
        this.canvas.width = this.boundPos.canvasWidth;
        this.canvas.height = this.boundPos.canvasHeight;

        this.ctx.save();
        this.ctx.fillStyle = null;
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
        this.ctx.restore();

        this.drawShadowInternal(false, true);
        this.drawNinePatchLines();
    }

    drawNinePatchLines() {
        const {
            outlineWidth
        } = this.input;
        let w = this.objectWidth;
        let h = this.objectHeight;
        let s = 0;
        let offsetX = this.getRelativeX();
        let offsetY = this.getRelativeY();
        let ninePatchLineWidth = 1;
        let width = this.canvas.width;
        let height = this.canvas.height;

        //Subtract outline width from content padding
        if (!this.isTransparentFill) {
            const outlineHalf = Math.round(outlineWidth / 2);
            w -= outlineWidth;
            h -= outlineWidth;
            offsetX += outlineHalf;
            offsetY += outlineHalf;
        }

        //Clear 1px frame around image for ninepatch pixels
        //Top
        this.ctx.clearRect(0, 0, width, ninePatchLineWidth);
        //Bottom
        this.ctx.clearRect(0, height - ninePatchLineWidth, width, ninePatchLineWidth);
        //Left
        this.ctx.clearRect(0, 0, ninePatchLineWidth, height);
        //Right
        this.ctx.clearRect(width - ninePatchLineWidth, 0, ninePatchLineWidth, height);

        this.ctx.strokeStyle = "black";
        this.ctx.lineWidth = ninePatchLineWidth * 2;

        this.ctx.beginPath();

        //Draw left
        s = h / 2;
        this.ctx.moveTo(0, Math.round(offsetY + s - NINE_PATCH_SIZING_WIDTH / 2));
        this.ctx.lineTo(0, Math.round(offsetY + s + NINE_PATCH_SIZING_WIDTH));

        //Draw top
        s = w / 2;
        this.ctx.moveTo(Math.round(offsetX + s - NINE_PATCH_SIZING_WIDTH / 2), 0);
        this.ctx.lineTo(Math.round(offsetX + s + NINE_PATCH_SIZING_WIDTH), 0);

        //Draw right
        this.ctx.moveTo(Math.round(width), Math.round(offsetY + (h)));
        this.ctx.lineTo(Math.round(width), Math.round(offsetY + h - (h - ninePatchLineWidth)));

        //Draw bottom
        this.ctx.moveTo(Math.round(offsetX + (w)), Math.round(height));
        this.ctx.lineTo(Math.round(offsetX + w - (w)), Math.round(height));

        this.ctx.closePath();
        this.ctx.stroke();

        //Clear right top corner
        this.ctx.clearRect(width - ninePatchLineWidth, 0, ninePatchLineWidth, ninePatchLineWidth);
        //Clear right bottom corner
        this.ctx.clearRect(width - ninePatchLineWidth, height - ninePatchLineWidth, ninePatchLineWidth, ninePatchLineWidth);
        //Clear left bottom corner
        this.ctx.clearRect(0, height - ninePatchLineWidth, ninePatchLineWidth, ninePatchLineWidth);
    }

    updateBounds() {
        const {
            paddingLeft,
            paddingRight,
            paddingTop,
            paddingBottom
        } = this.input;
        const w = this.objectWidth;
        const h = this.objectHeight;
        this.boundPos.leftPos = this.boundPos.topPos = Number.MAX_VALUE;
        this.boundPos.rightPos = this.boundPos.bottomPos = -1;

        let imgData = this.ctx.getImageData(0, 0, this.canvas.width, this.canvas.height);
        let imageWidth = imgData.width;
        let imageHeight = imgData.height;
        let imageData = imgData.data;

        //Iterate through all pixels in image
        //used to get image bounds (where shadow ends)
        for (let i = 0; i < imageData.length; i += 4) {
            if (imageData[i + 3] !== 0) { //check for non alpha pixel
                let x = (i / 4) % imageWidth;
                let y = Math.floor((i / 4) / imageWidth);

                if (x < this.boundPos.leftPos) {
                    this.boundPos.leftPos = x;
                } else if (x > this.boundPos.rightPos) {
                    this.boundPos.rightPos = x;
                }

                if (y < this.boundPos.topPos) {
                    this.boundPos.topPos = y;
                } else if (y > this.boundPos.bottomPos) {
                    this.boundPos.bottomPos = y;
                }
            }
        }

        let actualWidth = this.boundPos.rightPos - this.boundPos.leftPos;
        let actualHeight = this.boundPos.bottomPos - this.boundPos.topPos;
        let actualPaddingTop = imageHeight / 2 - h / 2 - this.boundPos.topPos;
        let actualPaddingBottom = this.boundPos.bottomPos - (imageHeight / 2 + h / 2);
        let actualPaddingLeft = imageWidth / 2 - w / 2 - this.boundPos.leftPos;
        let actualPaddingRight = this.boundPos.rightPos - (imageWidth / 2 + w / 2);

        let msg = ['actual size: [', actualWidth, actualHeight, ']',
            ' shadow [', actualPaddingTop, actualPaddingRight, actualPaddingBottom, actualPaddingLeft, ']'].join(' ');
        //show the actual size
        console.log(msg);
        //change to desire bounds
        if (paddingLeft !== 0) {
            this.boundPos.leftPos = (imageWidth - w) / 2 - paddingLeft;
        }
        if (paddingRight !== 0) {
            this.boundPos.rightPos = imageWidth / 2 + w / 2 + paddingRight;
        }
        if (paddingTop !== 0) {
            this.boundPos.topPos = (imageHeight - h) / 2 - paddingTop;
        }
        if (paddingBottom !== 0) {
            this.boundPos.bottomPos = imageHeight / 2 + h / 2 + paddingBottom;
        }

        this.boundPos.leftPos = this.boundPos.leftPos - 1;
        this.boundPos.topPos = this.boundPos.topPos - 1;
        this.boundPos.rightPos = imageWidth - this.boundPos.rightPos - 2;
        this.boundPos.bottomPos = imageHeight - this.boundPos.bottomPos - 2;

        //Calculate final canvas width and height
        this.boundPos.canvasWidth = Math.round(this.canvas.width - (this.boundPos.leftPos + this.boundPos.rightPos));
        this.boundPos.canvasHeight = Math.round(this.canvas.height - (this.boundPos.topPos + this.boundPos.bottomPos));

        //Add clipping If set
        let clipLeft = getRelativeX() + roundRadius.lowerLeft;
        let clipTop = getRelativeY() + roundRadius.upperLeft;
        let clipRight = boundPos.canvasWidth - objectWidth - getRelativeX() + roundRadius.lowerRight;
        let clipBottom = boundPos.canvasHeight - objectHeight - getRelativeY() + roundRadius.upperRight;

        this.boundPos.leftPos += clipLeft;
        this.boundPos.topPos += clipTop;
        this.boundPos.rightPos += clipRight;
        this.boundPos.bottomPos += clipBottom;

        this.boundPos.clipLeft = clipLeft;

        this.boundPos.canvasWidth -= clipLeft + clipRight;
        this.boundPos.canvasHeight -= clipBottom + clipTop;
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
        let x, y;
        this.ctx.save();
        if (this.isTransparentFill) {
            this.ctx.translate(OFFSET_FOR_TRANSPARENT, OFFSET_FOR_TRANSPARENT);
        }
        if (center) {
            x = centerPosX;
            y = centerPosY;
        } else {
            x = this.getRelativeX();
            y = this.getRelativeY();
        }
        if (this.boxResizeMode !== BoxResizeType.None) {
            x -= shadowDx;
            y -= shadowDy;
        }
        this.roundRect(x, y);
        if (!this.isTransparentFill) {
            this.ctx.shadowOffsetX = shadowDx;
            this.ctx.shadowOffsetY = shadowDy;
            this.ctx.shadowBlur = shadowBlur;
            this.ctx.shadowColor = toColorText(shadowColor);
        } else {
            this.ctx.shadowOffsetX = shadowDx - DrawingTask.OFFSET_FOR_TRANSPARENT;
            this.ctx.shadowOffsetY = shadowDy - DrawingTask.OFFSET_FOR_TRANSPARENT;
            this.ctx.shadowBlur = shadowBlur;
            this.ctx.shadowColor = toColorText(shadowColor);
        }
        this.ctx.fill();
        if (!this.isTransparentFill && outlineWidth > 0) {
            this.ctx.shadowOffsetX = 0;
            this.ctx.shadowOffsetY = 0;
            this.ctx.shadowBlur = 0;
            this.ctx.shadowColor = toColorText(0);
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

    roundRect(x, y) {
        const {
            roundLeftTop,
            roundRightTop,
            roundLeftBottom,
            roundRightBottom
        } = this.input;
        const w = this.objectWidth;
        const h = this.objectHeight;
        this.ctx.beginPath();
        this.ctx.moveTo(x + roundLeftTop, y);
        this.ctx.lineTo(x + w - roundRightTop, y);
        this.ctx.quadraticCurveTo(x + w, y, x + w, y + roundRightTop);
        this.ctx.lineTo(x + w, y + h - roundRightBottom);
        this.ctx.quadraticCurveTo(x + w, y + h, x + w - roundRightBottom, y + h);
        this.ctx.lineTo(x + roundLeftBottom, y + h);
        this.ctx.quadraticCurveTo(x, y + h, x, y + h - roundLeftBottom);
        this.ctx.lineTo(x, y + roundLeftTop);
        this.ctx.quadraticCurveTo(x, y, x + roundLeftTop, y);
        this.ctx.closePath();
    }

    async sendResponse() {
        let output;
        try {
            const blob = await new Promise(resolve => {
                // noinspection JSUnresolvedVariable
                if (this.canvas.toBlobHD) {
                    this.canvas.toBlobHD(resolve)
                } else {
                    this.canvas.toBlob(resolve)
                }
            });
            const base64 = await new Promise(resolve => {
                const reader = new FileReader();
                reader.onloadend = () => {
                    resolve(reader.result)
                };
                // noinspection JSCheckFunctionSignatures
                reader.readAsDataURL(blob);
            });
            output = {
                margin: [],
                imageData: base64
            };
        } catch (e) {
            output = {
                error: e.message
            };
        }
        // noinspection JSUnresolvedVariable
        if (typeof __taskManager__ !== "undefined") {
            // noinspection JSUnresolvedFunction,JSUnresolvedVariable
            __taskManager__.onTaskComplete(JSON.stringify(output, id));
        } else {
            console.log("output", output)
        }
    }

}

// noinspection JSUnusedGlobalSymbols
export default async function createNinePatch(input, id) {
    new DrawingTask(input, id).execute();
}
