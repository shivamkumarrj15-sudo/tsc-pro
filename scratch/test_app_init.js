const fs = require('fs');
const html = fs.readFileSync('index.html', 'utf8');

// Extract all <script> blocks
const scriptRegex = /<script>([\s\S]*?)<\/script>/gi;
let match;
let scriptIndex = 1;

while ((match = scriptRegex.exec(html)) !== null) {
    const code = match[1];
    try {
        new Function(code);
        console.log(`Script block #${scriptIndex} syntax is OK!`);
    } catch(err) {
        console.error(`Script block #${scriptIndex} error:`, err);
    }
    scriptIndex++;
}
