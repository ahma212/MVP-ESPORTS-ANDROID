def extract_nalus(data):
    nalus = []
    i = 0
    start = -1
    while i < len(data) - 2:
        if data[i] == 0 and data[i+1] == 0 and data[i+2] == 1:
            if start != -1:
                end = i - 1 if data[i-1] == 0 else i
                nalus.append(data[start:end])
            start = i + 3
            i += 3
        else:
            i += 1
    if start != -1 and start < len(data):
        nalus.append(data[start:])
    return nalus

sample = [0, 0, 0, 1, 10, 11, 12, 0, 0, 1, 13, 14, 0, 0, 0, 1, 15]
nalus = extract_nalus(sample)
for n in nalus:
    print(n)
