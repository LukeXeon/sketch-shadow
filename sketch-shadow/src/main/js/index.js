import "@babel/polyfill";

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
const NINE_PATCH_SIZING_WIDTH = 4;

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

    margin = [];

    boxResizeMode = BoxResizeType.None;

    constructor(input) {
        this.processInput(input);
        this.processObjectSize();
        this.prepareCanvas();
    }

    prepareCanvas() {
        this.canvas = document.createElement('canvas').transferControlToOffscreen();
        this.ctx = this.canvas.getContext("2d");
    }

    processInput(input) {
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
        this.input = json;
    }

    processObjectSize() {
        const {
            roundLeftTop,
            roundRightTop,
            roundLeftBottom,
            roundRightBottom
        } = this.input;
        this.objectWidth = 1 + Math.max(roundLeftTop, roundLeftBottom) + Math.max(roundRightTop, roundRightBottom);
        this.objectHeight = 1 + Math.max(roundLeftTop, roundRightTop) + Math.max(roundLeftBottom, roundRightBottom);
    }

    getRelativeX() {
        return Math.round((CANVAS_MAX_WIDTH / 2) - (this.objectWidth / 2) - this.boundPos.leftPos);
    }

    getRelativeY() {
        return Math.round((CANVAS_MAX_HEIGHT / 2) - (this.objectHeight / 2) - this.boundPos.topPos);
    }

    execute() {
        this.drawShadow();
        return this.createResponse();
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
            paddingBottom,
            roundLeftTop,
            roundRightTop,
            roundLeftBottom,
            roundRightBottom
        } = this.input;
        const w = this.objectWidth;
        const h = this.objectHeight;
        this.boundPos.leftPos = this.boundPos.topPos = Number.MAX_VALUE;
        this.boundPos.rightPos = this.boundPos.bottomPos = -1;

        const imgData = this.ctx.getImageData(0, 0, this.canvas.width, this.canvas.height);
        const imageWidth = imgData.width;
        const imageHeight = imgData.height;
        const imageData = imgData.data;

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

        this.margin = [actualPaddingLeft, actualPaddingTop, actualPaddingRight, actualPaddingBottom];

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
        let clipLeft = this.getRelativeX() + roundLeftBottom;
        let clipTop = this.getRelativeY() + roundLeftTop;
        let clipRight = this.boundPos.canvasWidth - this.objectWidth - this.getRelativeX() + roundRightBottom;
        let clipBottom = this.boundPos.canvasHeight - this.objectHeight - this.getRelativeY() + roundRightTop;

        this.boundPos.leftPos += clipLeft;
        this.boundPos.topPos += clipTop;
        this.boundPos.rightPos += clipRight;
        this.boundPos.bottomPos += clipBottom;

        this.boundPos.clipLeft = clipLeft;

        this.boundPos.canvasWidth -= clipLeft + clipRight;
        this.boundPos.canvasHeight -= clipBottom + clipTop;
    }

    setShadow(shadowDx, shadowDy, shadowBlur, shadowColor) {
        this.ctx.shadowOffsetX = shadowDx;
        this.ctx.shadowOffsetY = shadowDy;
        this.ctx.shadowBlur = shadowBlur;
        this.ctx.shadowColor = toColorText(shadowColor);
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
        const centerPosX = Math.round((this.canvas.width / 2) - (w / 2));
        const centerPosY = Math.round((this.canvas.height / 2) - (h / 2));
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
            this.setShadow(shadowDx, shadowDy, shadowBlur, shadowColor);
        } else {
            this.setShadow(
                shadowDx - OFFSET_FOR_TRANSPARENT,
                shadowDy - OFFSET_FOR_TRANSPARENT,
                shadowBlur,
                shadowColor
            );
        }
        this.ctx.fill();
        if (!this.isTransparentFill && outlineWidth > 0) {
            this.setShadow(0, 0, 0, 0);
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
        if (this.boxResizeMode !== BoxResizeType.None) {
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
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
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

    createResponse() {
        let base64;
        // noinspection JSUnresolvedVariable
        if (this.canvas.toDataURLHD) {
            base64 = this.canvas.toDataURLHD()
        } else {
            base64 = this.canvas.toDataURL()
        }
        return {
            margin: this.margin,
            imageData: base64
        };
    }
}

function createNinePatch(input) {
    try {
        return JSON.stringify(new DrawingTask(input).execute());
    } catch (e) {
        return JSON.stringify({error: e.message});
    }
}

global.createNinePatch = createNinePatch;
