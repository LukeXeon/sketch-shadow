import {Buffer} from 'buffer'
import crc32 from 'buffer-crc32'

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

class NinePatchChunk {
    wasDeserialized = 0;
    numXDivs = 0;
    numYDivs = 0;
    numColors = 0;
    xDivs = [];
    yDivs = [];
    paddingLeft = 0;
    paddingRight = 0;
    paddingTop = 0;
    paddingBottom = 0;
    colors = [];

    serialize() {
        let buffer = new Buffer(
            4 + this.numXDivs + this.numYDivs + 4 + 4 * this.numColors
        );
        let offset = 0;
        for (let value of [this.wasDeserialized, this.numXDivs, this.numYDivs, this.numColors]) {
            buffer.writeUIntBE(value, offset++, 1);
        }
        for (let i = 0; i < this.numXDivs; i++) {
            buffer.writeUIntBE(this.xDivs[i], offset++, 1);
        }
        for (let i = 0; i < this.numYDivs; i++) {
            buffer.writeUIntBE(this.yDivs[i], offset++, 1);
        }
        for (let padding of [this.paddingLeft, this.paddingRight, this.paddingTop, this.paddingBottom]) {
            buffer.writeInt32BE(padding, offset);
            offset += 4;
        }
        for (let i = 0; i < this.numColors; i++) {
            buffer.writeInt32BE(this.colors[i], offset);
            offset += 4;
        }
        return buffer;
    }
}

async function createNinePatch(input) {
    let canvas = document.getElementById("shadow_image");
    let ctx = canvas.getContext("2d");
    let blob = await new Promise(resolve => {
        canvas.toBlob(resolve)
    });
    let base64 = await new Promise(resolve => {
        let reader = new FileReader();
        reader.readAsDataURL(blob);
        reader.onloadend = () => {
            resolve(reader.result)
        }
    });
}
