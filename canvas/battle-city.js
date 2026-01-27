// Battle City Mobile - Canvas Game
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');

// Responsive canvas size
function resizeCanvas() {
    const maxWidth = window.innerWidth;
    const maxHeight = window.innerHeight - 200; // Leave space for controls
    const size = Math.min(maxWidth, maxHeight, 600);
    canvas.width = size;
    canvas.height = size;
}
resizeCanvas();
window.addEventListener('resize', resizeCanvas);

// Grid system (20x20 tiles)
const GRID_SIZE = 20;
const TILE_SIZE = canvas.width / GRID_SIZE;

// Game state
const game = {
    score: 0,
    lives: 3,
    level: 1,
    paused: false
};

// Player tank
const player = {
    x: 9,
    y: 18,
    dir: 0, // 0=up, 1=right, 2=down, 3=left
    speed: 0.1,
    shooting: false
};

// Enemies
const enemies = [];
const MAX_ENEMIES = 4;

// Bullets
const bullets = [];
const enemyBullets = [];

// Map (0=empty, 1=brick, 2=steel, 3=base)
const map = [];
for (let y = 0; y < GRID_SIZE; y++) {
    map[y] = [];
    for (let x = 0; x < GRID_SIZE; x++) {
        map[y][x] = 0;
    }
}

// Base at bottom center
map[19][9] = 3;
map[19][10] = 3;
map[18][9] = 3;
map[18][10] = 3;

// Some brick walls
for (let i = 0; i < 30; i++) {
    const x = Math.floor(Math.random() * GRID_SIZE);
    const y = Math.floor(Math.random() * (GRID_SIZE - 4));
    if (map[y][x] === 0) map[y][x] = 1;
}

// Mobile Controls
const joystick = document.getElementById('joystick');
const stick = document.getElementById('stick');
const fireBtn = document.getElementById('fireBtn');

let joystickActive = false;
let joystickAngle = 0;
let joystickDistance = 0;

joystick.addEventListener('touchstart', handleJoystickStart, { passive: false });
joystick.addEventListener('touchmove', handleJoystickMove, { passive: false });
joystick.addEventListener('touchend', handleJoystickEnd, { passive: false });

// Add mouse support as fallback for Safari issues
joystick.addEventListener('mousedown', handleJoystickStart);
joystick.addEventListener('mousemove', handleJoystickMove);
joystick.addEventListener('mouseup', handleJoystickEnd);
joystick.addEventListener('mouseleave', handleJoystickEnd);

fireBtn.addEventListener('touchstart', (e) => { e.preventDefault(); shoot(); }, { passive: false });
fireBtn.addEventListener('mousedown', (e) => { e.preventDefault(); shoot(); });

function handleJoystickStart(e) {
    e.preventDefault();
    joystickActive = true;
    console.log('Joystick START - active:', joystickActive);
    // Force immediate move handling
    handleJoystickMove(e);
}

function handleJoystickMove(e) {
    e.preventDefault();
    if (!joystickActive) return;
    
    const rect = joystick.getBoundingClientRect();
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;
    
    // Support both touch and mouse events
    const touch = e.touches ? e.touches[0] : e;
    const x = touch.clientX - rect.left - centerX;
    const y = touch.clientY - rect.top - centerY;
    
    const distance = Math.min(Math.sqrt(x*x + y*y), 45);
    const angle = Math.atan2(y, x);
    
    const stickX = Math.cos(angle) * distance;
    const stickY = Math.sin(angle) * distance;
    
    stick.style.transform = `translate(calc(-50% + ${stickX}px), calc(-50% + ${stickY}px))`;
    
    // Update player direction and distance
    joystickAngle = angle;
    joystickDistance = distance;
    
    if (Math.abs(y) > Math.abs(x)) {
        player.dir = y > 0 ? 2 : 0; // down : up
    } else {
        player.dir = x > 0 ? 1 : 3; // right : left
    }
    
    console.log('Joystick MOVE - dir:', player.dir, 'dist:', distance.toFixed(1));
}

function handleJoystickEnd(e) {
    e.preventDefault();
    joystickActive = false;
    joystickDistance = 0;
    stick.style.transform = 'translate(-50%, -50%)';
    console.log('Joystick END');
}

// Shoot
function shoot() {
    if (player.shooting) return;
    player.shooting = true;
    
    const bullet = {
        x: player.x + 0.5,
        y: player.y + 0.5,
        dir: player.dir,
        speed: 0.15
    };
    bullets.push(bullet);
    
    setTimeout(() => player.shooting = false, 300);
}

// Spawn enemy
function spawnEnemy() {
    if (enemies.length >= MAX_ENEMIES) return;
    
    const spawnPoints = [
        {x: 0, y: 0},
        {x: 9, y: 0},
        {x: 19, y: 0}
    ];
    
    const spawn = spawnPoints[Math.floor(Math.random() * spawnPoints.length)];
    
    enemies.push({
        x: spawn.x,
        y: spawn.y,
        dir: 2, // Start going down
        speed: 0.05,
        shootTimer: 0
    });
}

// Update game
function update(deltaTime) {
    if (game.paused) return;
    
    // Move player (active flag OR distance check for better Safari support)
    if (joystickActive && joystickDistance > 5) {
        const dx = player.dir === 1 ? player.speed : player.dir === 3 ? -player.speed : 0;
        const dy = player.dir === 2 ? player.speed : player.dir === 0 ? -player.speed : 0;
        
        const newX = player.x + dx;
        const newY = player.y + dy;
        
        if (canMove(newX, newY)) {
            player.x = newX;
            player.y = newY;
        }
    }
    
    // Move bullets
    bullets.forEach((b, i) => {
        const dx = b.dir === 1 ? b.speed : b.dir === 3 ? -b.speed : 0;
        const dy = b.dir === 2 ? b.speed : b.dir === 0 ? -b.speed : 0;
        
        b.x += dx;
        b.y += dy;
        
        // Remove if out of bounds
        if (b.x < 0 || b.x >= GRID_SIZE || b.y < 0 || b.y >= GRID_SIZE) {
            bullets.splice(i, 1);
            return;
        }
        
        // Hit brick
        const tileX = Math.floor(b.x);
        const tileY = Math.floor(b.y);
        if (map[tileY] && map[tileY][tileX] === 1) {
            map[tileY][tileX] = 0;
            bullets.splice(i, 1);
            return;
        }
        
        // Hit steel
        if (map[tileY] && map[tileY][tileX] === 2) {
            bullets.splice(i, 1);
            return;
        }
        
        // Hit enemy
        enemies.forEach((e, ei) => {
            if (Math.abs(b.x - e.x - 0.5) < 0.5 && Math.abs(b.y - e.y - 0.5) < 0.5) {
                enemies.splice(ei, 1);
                bullets.splice(i, 1);
                game.score += 100;
                updateUI();
            }
        });
    });
    
    // Move enemies
    enemies.forEach((e, i) => {
        // Simple AI - random movement
        if (Math.random() < 0.02) {
            e.dir = Math.floor(Math.random() * 4);
        }
        
        const dx = e.dir === 1 ? e.speed : e.dir === 3 ? -e.speed : 0;
        const dy = e.dir === 2 ? e.speed : e.dir === 0 ? -e.speed : 0;
        
        const newX = e.x + dx;
        const newY = e.y + dy;
        
        if (canMove(newX, newY)) {
            e.x = newX;
            e.y = newY;
        } else {
            e.dir = Math.floor(Math.random() * 4);
        }
        
        // Enemy shoot
        e.shootTimer += deltaTime;
        if (e.shootTimer > 2000) {
            e.shootTimer = 0;
            enemyBullets.push({
                x: e.x + 0.5,
                y: e.y + 0.5,
                dir: e.dir,
                speed: 0.1
            });
        }
    });
    
    // Spawn enemies
    if (enemies.length < MAX_ENEMIES && Math.random() < 0.01) {
        spawnEnemy();
    }
}

function canMove(x, y) {
    if (x < 0 || x >= GRID_SIZE - 1 || y < 0 || y >= GRID_SIZE - 1) return false;
    
    const tileX = Math.floor(x);
    const tileY = Math.floor(y);
    
    // Check all 4 corners of tank (tank is 1x1)
    for (let dy = 0; dy <= 1; dy++) {
        for (let dx = 0; dx <= 1; dx++) {
            const checkY = tileY + dy;
            const checkX = tileX + dx;
            if (map[checkY] && map[checkY][checkX] !== 0) return false;
        }
    }
    
    return true;
}

// Draw
function draw() {
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Draw map
    for (let y = 0; y < GRID_SIZE; y++) {
        for (let x = 0; x < GRID_SIZE; x++) {
            const tile = map[y][x];
            if (tile === 0) continue;
            
            if (tile === 1) ctx.fillStyle = '#8B4513'; // Brick
            if (tile === 2) ctx.fillStyle = '#A9A9A9'; // Steel
            if (tile === 3) ctx.fillStyle = '#FFD700'; // Base
            
            ctx.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        }
    }
    
    // Draw player
    ctx.fillStyle = '#FFD700';
    ctx.fillRect(player.x * TILE_SIZE, player.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
    
    // Draw barrel direction
    ctx.fillStyle = '#FFA500';
    if (player.dir === 0) ctx.fillRect(player.x * TILE_SIZE + TILE_SIZE/3, player.y * TILE_SIZE, TILE_SIZE/3, TILE_SIZE/2);
    if (player.dir === 1) ctx.fillRect(player.x * TILE_SIZE + TILE_SIZE/2, player.y * TILE_SIZE + TILE_SIZE/3, TILE_SIZE/2, TILE_SIZE/3);
    if (player.dir === 2) ctx.fillRect(player.x * TILE_SIZE + TILE_SIZE/3, player.y * TILE_SIZE + TILE_SIZE/2, TILE_SIZE/3, TILE_SIZE/2);
    if (player.dir === 3) ctx.fillRect(player.x * TILE_SIZE, player.y * TILE_SIZE + TILE_SIZE/3, TILE_SIZE/2, TILE_SIZE/3);
    
    // Draw enemies
    ctx.fillStyle = '#F00';
    enemies.forEach(e => {
        ctx.fillRect(e.x * TILE_SIZE, e.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
    });
    
    // Draw bullets
    ctx.fillStyle = '#FFF';
    bullets.forEach(b => {
        ctx.fillRect(b.x * TILE_SIZE - 2, b.y * TILE_SIZE - 2, 4, 4);
    });
    
    enemyBullets.forEach(b => {
        ctx.fillRect(b.x * TILE_SIZE - 2, b.y * TILE_SIZE - 2, 4, 4);
    });
}

function updateUI() {
    document.getElementById('score').textContent = 'SCORE: ' + game.score;
    const hearts = '❤️'.repeat(game.lives);
    document.getElementById('lives').textContent = hearts;
}

// Game loop
let lastTime = Date.now();
function gameLoop() {
    const now = Date.now();
    const deltaTime = now - lastTime;
    lastTime = now;
    
    update(deltaTime);
    draw();
    
    requestAnimationFrame(gameLoop);
}

// Start
spawnEnemy();
updateUI();
gameLoop();
