const CANVAS_MIN_WIDTH = 10, CANVAS_MIN_HEIGHT = 10;
const CANVAS_MAX_WIDTH = 500, CANVAS_MAX_HEIGHT = 500;

function setShadow(ctx, x, y, b, c) {
    ctx.shadowOffsetX = x;
    ctx.shadowOffsetY = y;
    ctx.shadowBlur = b;
    ctx.shadowColor = c;
}

function drawShadowInternal(ctx,input,w,h,center, translate) {
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
    const centerPosX = Math.round((canvas.width / 2) - (w / 2));
    const centerPosY = Math.round((canvas.height / 2) - (h / 2));
    ctx.save();

}

class DrawingTask {
    constructor(input, id) {
        const {
            roundLeftTop,
            roundRightTop,
            roundLeftBottom,
            roundRightBottom
        } = input;
        this.canvas = document.createElement('canvas');
        this.ctx = canvas.getContext("2d");
        this.input = input;
        this.id = id;
        this.width = 1 + Math.max(roundLeftTop + roundRightTop, roundLeftBottom + roundRightBottom);
        this.height = 1 + Math.max(roundLeftTop + roundLeftBottom, roundRightTop + roundRightBottom);
    }

    execute() {

        this.sendResponse().then(() => {
            console.log("complete: task id=" + this.id);
        });
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
        this.ctx.lineTo(x + this.width - roundRightTop, y);
        this.ctx.quadraticCurveTo(x + this.width, y, x + this.width, y + roundRightTop);
        this.ctx.lineTo(x + this.width, y + this.height - roundRightBottom);
        this.ctx.quadraticCurveTo(x + this.width, y + this.height, x + this.width - roundRightBottom, y + this.height);
        this.ctx.lineTo(x + roundLeftBottom, y + this.height);
        this.ctx.quadraticCurveTo(x, y + this.height, x, y + this.height - roundLeftBottom);
        this.ctx.lineTo(x, y + roundLeftTop);
        this.ctx.quadraticCurveTo(x, y, x + roundLeftTop, y);
        this.ctx.closePath();
    }
}

async function createNinePatch(input, id) {
    const task = new DrawingTask(input, id);
    task.execute();
}
