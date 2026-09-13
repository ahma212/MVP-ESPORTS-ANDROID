from PIL import Image, ImageDraw

def create_icon(size, filename, is_round):
    # Create image with transparent background
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Navy background
    bg_color = "#0A192F"
    if is_round:
        draw.ellipse([0, 0, size, size], fill=bg_color)
    else:
        # Rounded rect
        r = size * 0.1
        draw.rounded_rectangle([0, 0, size, size], radius=r, fill=bg_color)
        
    # Draw Shield
    cx, cy = size / 2, size / 2
    # Scale from 108 coordinates
    scale = size / 108.0
    
    cyan = "#00E5FF"
    width = int(4 * scale)
    
    # Shield points
    p1 = (54*scale, 18*scale)
    p2 = (88*scale, 28*scale)
    p3 = (88*scale, 58*scale)
    p4 = (20*scale, 58*scale)
    p5 = (20*scale, 28*scale)
    
    # For polygon, we approximate the curve. Or just draw lines
    draw.polygon([p1, p2, p3, (54*scale, 98*scale), p4, p5], outline=cyan, width=width)
    
    # L
    draw.line([(38*scale, 44*scale), (38*scale, 66*scale), (48*scale, 66*scale)], fill=cyan, width=width, joint="curve")
    
    # A
    draw.line([(56*scale, 66*scale), (62*scale, 44*scale), (68*scale, 66*scale)], fill=cyan, width=width, joint="curve")
    draw.line([(59*scale, 58*scale), (65*scale, 58*scale)], fill=cyan, width=width, joint="curve")
    
    img.save(filename)

size = 192
create_icon(size, "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", False)
create_icon(size, "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png", True)
