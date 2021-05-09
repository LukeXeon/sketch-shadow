function roundRect(ctx, x, y, width, height, radius) {
    let cornerRadius = {upperLeft: 0, upperRight: 0, lowerLeft: 0, lowerRight: 0};
    if (typeof radius === "object") {
        for (let side of radius) {
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

function setShadow(ctx, x, y, b, c) {
    ctx.shadowOffsetX = x;
    ctx.shadowOffsetY = y;
    ctx.shadowBlur = b;
    ctx.shadowColor = c;
}

async function createNinePatch(input, id) {
    const {
        roundLeftTop,
        roundRightTop,
        roundLeftBottom,
        roundRightBottom,
        shadowBlur,
        shadowColor,
        shadowDx,
        shadowDy,
        backgroundFillColor,
        fillColor,
        outlineColor,
        outlineWidth,
        paddingLeft,
        paddingRight,
        paddingTop,
        paddingBottom
    } = input;
    const width = 1 + Math.max(roundLeftTop + roundRightTop, roundLeftBottom + roundRightBottom);
    const height = 1 + Math.max(roundLeftTop + roundLeftBottom, roundRightTop + roundRightBottom);
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext("2d");
    const centerPosX = Math.round((canvas.width / 2) - (width / 2));
    const centerPosY = Math.round((canvas.height / 2) - (height / 2));
    ctx.save();

    const blob = await new Promise(resolve => {
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
