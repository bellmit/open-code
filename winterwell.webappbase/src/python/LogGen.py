from asyncore import write
import time
import random

randomWords = "Bilbo was very rich and very peculiar, and had been the wonder of the Shire for sixty years, ever since his remarkable disappearance and unexpected return. The riches he had brought back from his travels had now become a local legend, and it was popularly believed, whatever the old folk might say, that the Hill at Bag End was full of tunnels stuffed with treasure. And if that was not enough for fame, there was also his prolonged vigour to marvel at. Time wore on, but it seemed to have little effect on Mr. Baggins. At ninety he was much the same as at fifty. At ninety-nine they began to call him well-preserved ; but unchanged would have been nearer the mark. There were some that shook their heads and thought this was too much of a good thing; it seemed unfair that anyone should possess (apparently) perpetual youth as well as (reputedly) inexhaustible wealth."
randomList = randomWords.split()

while True:
	fh = open('/home/wing/Desktop/read.txt', 'a')
	timeStr = time.strftime('%b %d, %Y %I:%M:%S %p')
	fh.writelines(timeStr + ' ' + random.choice(randomList) + ' ' + random.choice(randomList) + ' ' + random.choice(randomList) + '\n')
	print('Writing...')
	fh.close
	time.sleep(5)