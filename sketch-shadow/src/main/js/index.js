function createNinePatch(args) {
    let canvas = document.createElement("canvas");
    let ctx = canvas.getContext("2d");

    let BOX_RESIZE_TYPE = {None: 0, Right: 1, Bottom: 2, Corner: 3};

    let boundPos = {leftPos: -1, topPos: -1, rightPos: -1, bottomPos: -1, canvasWidth: -1, canvasHeight: -1};
    let clipSide = {left: false, top: false, right: false, bottom: false};
    let shadowColor, fillColor, backgroundFillColor, outlineColor, shadowBlur, shadowOffsetX, shadowOffsetY,
        outlineWidth, isTransparentFill, roundRadius, hideNinepatches,
        showContentArea;
    let objectWidth = 200, objectHeight = 200;
    let boxResizeMode = 0, boxResizeData = null, BOX_ANCHOR = 6;

    let paddingLeft = 0;
    let paddingRight = 0;
    let paddingTop = 0;
    let paddingBottom = 0;
    let margin = [];

    let CANVAS_MIN_WIDTH = 10, CANVAS_MIN_HEIGHT = 10;
    let CANVAS_MAX_WIDTH = 500, CANVAS_MAX_HEIGHT = 500;
    let CONTENT_AREA_COLOR = "rgba(53, 67, 172, 0.6)";
    let NINEPATCH_SIZING_WIDTH = 4;

    function toColorText(color) {
        if (color instanceof Number || typeof color === "number") {
            const alpha = color >> 24 & 0xff;
            const red = color >> 16 & 0xff;
            const green = color >> 8 & 0xff;
            const blue = color & 0xff;
            return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
        } else if (color instanceof String || typeof color === 'string') {
            return color;
        } else {
            return 'rgba(0,0,0,0)';
        }
    }

    function roundRect(ctx, x, y, width, height, radius) {
        let cornerRadius = {upperLeft: 0, upperRight: 0, lowerLeft: 0, lowerRight: 0};

        if (typeof radius === "object") {
            for (let side of Object.keys(radius)) {
                cornerRadius[side] = radius[side];
            }
        }

        ctx.beginPath();
        ctx.moveTo(x + cornerRadius.upperLeft, y);
        ctx.lineTo(x + width - cornerRadius.upperRight, y);
        ctx.quadraticCurveTo(x + width, y, x + width, y + cornerRadius.upperRight);
        ctx.lineTo(x + width, y + height - cornerRadius.lowerRight);
        ctx.quadraticCurveTo(x + width, y + height, x + width - cornerRadius.lowerRight, y + height);
        ctx.lineTo(x + cornerRadius.lowerLeft, y + height);
        ctx.quadraticCurveTo(x, y + height, x, y + height - cornerRadius.lowerLeft);
        ctx.lineTo(x, y + cornerRadius.upperLeft);
        ctx.quadraticCurveTo(x, y, x + cornerRadius.upperLeft, y);
        ctx.closePath();
    }

    function setShadow(x, y, b, c) {
        ctx.shadowOffsetX = x;
        ctx.shadowOffsetY = y;
        ctx.shadowBlur = b;
        ctx.shadowColor = c;
    }

    function exportAsDataURL() {
        if (canvas.toDataURLHD) {
            return canvas.toDataURLHD()
        } else {
            return canvas.toDataURL()
        }
    }

    function predraw(w, h, radius) {
        canvas.width = CANVAS_MAX_WIDTH;
        canvas.height = CANVAS_MAX_HEIGHT;

        let transparentTmp = isTransparentFill;
        isTransparentFill = false;
        drawShadowInternal(w, h, radius, true);

        updateBounds(w, h);

        isTransparentFill = transparentTmp;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
    }

    function drawShadow(w, h, radius, fast) {
        let paddingValues = getPaddingValues();

        //First time draw with filled background
        //for calculating final size of ninepatch
        if (!fast) {
            predraw(w, h, radius);
        }

        //Set canvas size to calculated size
        canvas.width = boundPos.canvasWidth;
        canvas.height = boundPos.canvasHeight;

        ctx.save();
        ctx.fillStyle = backgroundFillColor;
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.restore();

        drawShadowInternal(w, h, radius, false, true);

        drawNinepatchLines(w, h, paddingValues);


        if (showContentArea) {
            drawContentArea(w, h, paddingValues);
        }
    }

    function drawContentArea(w, h, paddingValues) {
        w -= outlineWidth;
        h -= outlineWidth;
        ctx.fillStyle = CONTENT_AREA_COLOR;
        let outlineHalf = Math.round(outlineWidth / 2);
        let x = getRelativeX() + outlineHalf;
        let y = getRelativeY() + outlineHalf;
        let xPad = paddingValues.horizontalLeft * w;
        let yPad = paddingValues.verticalTop * h;
        ctx.fillRect(x + xPad, y + yPad,
            w - (w * paddingValues.horizontalRight) - xPad, h - (h * paddingValues.verticalBottom) - yPad);
    }

    function drawShadowInternal(w, h, radius, center, translate) {
        let centerPosX = Math.round((canvas.width / 2) - (w / 2));
        let centerPosY = Math.round((canvas.height / 2) - (h / 2));
        let x = 0, y = 0;
        let offsetForTransparent = -9999;

        ctx.save();
        if (isTransparentFill) ctx.translate(offsetForTransparent, offsetForTransparent);

        if (center) {
            x = centerPosX;
            y = centerPosY;
        } else if (translate) {
            x = getRelativeX();
            y = getRelativeY();
        }
        if (boxResizeMode !== BOX_RESIZE_TYPE.None) {
            x -= shadowOffsetX;
            y -= shadowOffsetY;
        }
        roundRect(ctx, x, y, w, h, radius);

        if (!isTransparentFill) {
            setShadow(shadowOffsetX, shadowOffsetY, shadowBlur, shadowColor);
        } else {
            setShadow(shadowOffsetX - offsetForTransparent, shadowOffsetY - offsetForTransparent, shadowBlur, shadowColor);
        }

        ctx.fill();

        if (!isTransparentFill && outlineWidth > 0) {
            setShadow(0, 0, 0, 0);
            ctx.strokeStyle = outlineColor;
            ctx.lineWidth = outlineWidth;
            ctx.stroke();
        }

        ctx.restore();

        ctx.save();

        ctx.globalCompositeOperation = 'destination-out';
        if (center) {
            x = centerPosX;
            y = centerPosY;
        } else if (translate) {
            x = getRelativeX();
            y = getRelativeY();
        }
        if (boxResizeMode !== BOX_RESIZE_TYPE.None) {
            x -= shadowOffsetX;
            y -= shadowOffsetY;
        }
        roundRect(ctx, x, y, w, h, radius);
        ctx.fill();
        ctx.restore();

        if (!isTransparentFill) {
            ctx.save();
            ctx.fillStyle = fillColor;
            roundRect(ctx, x, y, w, h, radius);
            ctx.fill();
            ctx.restore();
        }
    }

    function getRelativeX() {
        return Math.round((CANVAS_MAX_WIDTH / 2) - (objectWidth / 2) - boundPos.leftPos);
    }

    function getRelativeY() {
        return Math.round((CANVAS_MAX_HEIGHT / 2) - (objectHeight / 2) - boundPos.topPos);
    }

    function updateBounds(w, h) {
        boundPos.leftPos = boundPos.topPos = Number.MAX_VALUE;
        boundPos.rightPos = boundPos.bottomPos = -1;

        let imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        let imageWidth = imgData.width;
        let imageHeight = imgData.height;
        let imageData = imgData.data;

        //Iterate through all pixels in image
        //used to get image bounds (where shadow ends)
        for (let i = 0; i < imageData.length; i += 4) {
            if (imageData[i + 3] !== 0) { //check for non alpha pixel
                let x = (i / 4) % imageWidth;
                let y = Math.floor((i / 4) / imageWidth);

                if (x < boundPos.leftPos) {
                    boundPos.leftPos = x;
                } else if (x > boundPos.rightPos) {
                    boundPos.rightPos = x;
                }

                if (y < boundPos.topPos) {
                    boundPos.topPos = y;
                } else if (y > boundPos.bottomPos) {
                    boundPos.bottomPos = y;
                }
            }
        }

        let actualWidth = boundPos.rightPos - boundPos.leftPos;
        let actualHeight = boundPos.bottomPos - boundPos.topPos;
        let actualPaddingTop = imageHeight / 2 - h / 2 - boundPos.topPos;
        let actualPaddingBottom = boundPos.bottomPos - (imageHeight / 2 + h / 2);
        let actualPaddingLeft = imageWidth / 2 - w / 2 - boundPos.leftPos;
        let actualPaddingRight = boundPos.rightPos - (imageWidth / 2 + w / 2);

        let msg = ['actual size: [', actualWidth, actualHeight, ']',
            ' shadow [', actualPaddingTop, actualPaddingRight, actualPaddingBottom, actualPaddingLeft, ']'].join(' ');
        console.log(msg);
        margin = [actualPaddingLeft, actualPaddingTop, actualPaddingRight, actualPaddingBottom];
        //change to desire bounds
        if (paddingLeft !== 0) {
            boundPos.leftPos = (imageWidth - w) / 2 - paddingLeft;
        }
        if (paddingRight !== 0) {
            boundPos.rightPos = imageWidth / 2 + w / 2 + paddingRight;
        }
        if (paddingTop !== 0) {
            boundPos.topPos = (imageHeight - h) / 2 - paddingTop;
        }
        if (paddingBottom !== 0) {
            boundPos.bottomPos = imageHeight / 2 + h / 2 + paddingBottom;
        }

        boundPos.leftPos = boundPos.leftPos - 1;
        boundPos.topPos = boundPos.topPos - 1;
        boundPos.rightPos = imageWidth - boundPos.rightPos - 2;
        boundPos.bottomPos = imageHeight - boundPos.bottomPos - 2;

        //Calculate final canvas width and height
        boundPos.canvasWidth = Math.round(canvas.width - (boundPos.leftPos + boundPos.rightPos));
        boundPos.canvasHeight = Math.round(canvas.height - (boundPos.topPos + boundPos.bottomPos));

        //Add clipping If set
        let clipLeft = clipSide.left ? getRelativeX() + roundRadius.lowerLeft : 0;
        let clipTop = clipSide.top ? getRelativeY() + roundRadius.upperLeft : 0;
        let clipRight = clipSide.right ? boundPos.canvasWidth - objectWidth - getRelativeX() + roundRadius.lowerRight : 0;
        let clipBottom = clipSide.bottom ? boundPos.canvasHeight - objectHeight - getRelativeY() + roundRadius.upperRight : 0;

        boundPos.leftPos += clipLeft;
        boundPos.topPos += clipTop;
        boundPos.rightPos += clipRight;
        boundPos.bottomPos += clipBottom;

        boundPos.canvasWidth -= clipLeft + clipRight;
        boundPos.canvasHeight -= clipBottom + clipTop;
    }

    function getPaddingValues() {
        let input = JSON.parse(args);
        let rightPad = input['rightPad'] || [0, 100];
        let bottomPad = input['bottomPad'] || [0, 100];
        let rightTop = (rightPad[0] / 100);
        let rightBottom = ((100 - rightPad[1]) / 100);
        let bottomLeft = (bottomPad[0] / 100);
        let bottomRight = ((100 - bottomPad[1]) / 100);

        return {
            verticalTop: rightTop, verticalBottom: rightBottom,
            horizontalLeft: bottomLeft, horizontalRight: bottomRight
        };
    }

    function drawNinepatchLines(w, h, paddingValues) {
        if (hideNinepatches) {
            return;
        }

        let s = 0;
        let offsetX = getRelativeX();
        let offsetY = getRelativeY();
        let ninepatchLineWidth = 1;
        let width = canvas.width;
        let height = canvas.height;

        //Subtract outline width from content padding
        if (!isTransparentFill) {
            let outlineHalf = Math.round(outlineWidth / 2);
            w -= outlineWidth;
            h -= outlineWidth;
            offsetX += outlineHalf;
            offsetY += outlineHalf;
        }

        //Clear 1px frame around image for ninepatch pixels
        //Top
        ctx.clearRect(0, 0, width, ninepatchLineWidth);
        //Bottom
        ctx.clearRect(0, height - ninepatchLineWidth, width, ninepatchLineWidth);
        //Left
        ctx.clearRect(0, 0, ninepatchLineWidth, height);
        //Right
        ctx.clearRect(width - ninepatchLineWidth, 0, ninepatchLineWidth, height);

        ctx.strokeStyle = "black";
        ctx.lineWidth = ninepatchLineWidth * 2;

        ctx.beginPath();

        //Draw left
        s = h / 2;
        ctx.moveTo(0, Math.round(offsetY + s - NINEPATCH_SIZING_WIDTH / 2));
        ctx.lineTo(0, Math.round(offsetY + s + NINEPATCH_SIZING_WIDTH));

        //Draw top
        s = w / 2;
        ctx.moveTo(Math.round(offsetX + s - NINEPATCH_SIZING_WIDTH / 2), 0);
        ctx.lineTo(Math.round(offsetX + s + NINEPATCH_SIZING_WIDTH), 0);

        //Draw right
        ctx.moveTo(Math.round(width), Math.round(offsetY + (h * paddingValues.verticalTop)));
        ctx.lineTo(Math.round(width), Math.round(offsetY + h - (h * paddingValues.verticalBottom - ninepatchLineWidth)));

        //Draw bottom
        ctx.moveTo(Math.round(offsetX + (w * paddingValues.horizontalLeft)), Math.round(height));
        ctx.lineTo(Math.round(offsetX + w - (w * paddingValues.horizontalRight)), Math.round(height));

        ctx.closePath();
        ctx.stroke();

        //Clear right top corner
        ctx.clearRect(width - ninepatchLineWidth, 0, ninepatchLineWidth, ninepatchLineWidth);
        //Clear right bottom corner
        ctx.clearRect(width - ninepatchLineWidth, height - ninepatchLineWidth, ninepatchLineWidth, ninepatchLineWidth);
        //Clear left bottom corner
        ctx.clearRect(0, height - ninepatchLineWidth, ninepatchLineWidth, ninepatchLineWidth);
    }

    function redraw(fast) {
        let input = JSON.parse(args);
        //Limit ranges for input
        let minRadius = 0, maxRadius = 500;
        let minOffset = -500, maxOffset = 500;
        let minBlur = 0, maxBlur = 500;
        let minOutlineW = 0, maxOutlineW = 99;

        shadowBlur = parseFloatAndClamp(input['shadowBlur'], minBlur, maxBlur);
        shadowOffsetX = parseFloatAndClamp(input['shadowDx'], minOffset, maxOffset, 0);
        shadowOffsetY = parseFloatAndClamp(input['shadowDy'], minOffset, maxOffset, 0);
        outlineWidth = parseFloatAndClamp(input['outlineWidth'], minOutlineW, maxOutlineW);

        shadowColor = toColorText(input['shadowColor']);
        fillColor = toColorText(input['fillColor']);
        backgroundFillColor = toColorText(input['backgroundFillColor']);
        outlineColor = toColorText(input['outlineColor']);

        isTransparentFill = (fillColor instanceof String || typeof fillColor === 'string') && input['fillColor'] !== 0;

        roundRadius = {
            upperLeft: parseFloatAndClamp(input['roundLeftTop'], minRadius, maxRadius),
            upperRight: parseFloatAndClamp(input['roundRightTop'], minRadius, maxRadius),
            lowerLeft: parseFloatAndClamp(input['roundLeftBottom'], minRadius, maxRadius),
            lowerRight: parseFloatAndClamp(input['roundRightBottom'], minRadius, maxRadius)
        };

        paddingTop = parseFloatAndClamp(input['paddingTop'], 0, CANVAS_MAX_WIDTH, 0);
        paddingBottom = parseFloatAndClamp(input['paddingBottom'], 0, CANVAS_MAX_WIDTH, 0);
        paddingLeft = parseFloatAndClamp(input['paddingLeft'], 0, CANVAS_MAX_WIDTH, 0);
        paddingRight = parseFloatAndClamp(input['paddingRight'], 0, CANVAS_MAX_WIDTH, 0);

        objectHeight = Math.max(roundRadius.upperLeft, roundRadius.upperRight) + Math.max(roundRadius.lowerLeft, roundRadius.lowerRight) + 1;
        objectWidth = Math.max(roundRadius.lowerLeft, roundRadius.upperLeft) + Math.max(roundRadius.upperRight, roundRadius.lowerRight) + 1;

        drawShadow(objectWidth, objectHeight, roundRadius, fast);
    }

    function parseFloatAndClamp(val, min, max, noneValue) {
        let num = parseFloat(val);
        if (isNaN(num)) {
            return (typeof noneValue !== "undefined") ? noneValue : min;
        } else {
            return Math.min(Math.max(min, val), max);
        }
    }

    redraw();

    return {
        margin,
        imageData: exportAsDataURL()
    };
}

global.createNinePatch = createNinePatch;
